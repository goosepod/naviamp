// Native ABI probe only: no Naviamp product policy, UI or settings implementation.
#import <UIKit/UIKit.h>
#import <AVFoundation/AVFoundation.h>
#import "bass.h"
#include <math.h>

static volatile BOOL ended = NO;
static void CALLBACK streamEnded(HSYNC sync, DWORD channel, DWORD data, void *user) {
    ended = YES;
}

static NSDictionary *probe(void) {
    NSMutableDictionary *r = [NSMutableDictionary dictionary];
    r[@"version"] = @(BASS_GetVersion());
    r[@"mixerLinked"] = @NO;
    r[@"audibilityVerified"] = @NO;
    NSError *sessionError = nil;
    AVAudioSession *session = AVAudioSession.sharedInstance;
    BOOL category = [session setCategory:AVAudioSessionCategoryPlayback error:&sessionError];
    BOOL active = category && [session setActive:YES error:&sessionError];
    r[@"audioSession"] = @(active);
    if (sessionError) r[@"audioSessionError"] = sessionError.description;
    BOOL initialized = active && BASS_Init(-1, 48000, 0, NULL, NULL);
    r[@"initialized"] = @(initialized);
    if (!initialized) {
        r[@"bassError"] = @(BASS_ErrorGetCode());
        return r;
    }
    NSString *resources = NSBundle.mainBundle.resourcePath;
    HPLUGIN flac = BASS_PluginLoad([NSBundle.mainBundle.privateFrameworksPath stringByAppendingPathComponent:@"bassflac.framework/bassflac"].UTF8String, 0);
    HPLUGIN opus = BASS_PluginLoad([NSBundle.mainBundle.privateFrameworksPath stringByAppendingPathComponent:@"bassopus.framework/bassopus"].UTF8String, 0);
    r[@"flacRegistered"] = @(flac != 0);
    r[@"opusRegistered"] = @(opus != 0);
    HSTREAM stream = BASS_StreamCreateFile(FALSE, [resources stringByAppendingPathComponent:@"fixture.wav"].UTF8String, 0, 0, BASS_SAMPLE_FLOAT);
    r[@"streamCreated"] = @(stream != 0);
    if (stream) {
        // Exercise a real playback channel silently; this does not establish acoustic output.
        r[@"volume"] = @(BASS_ChannelSetAttribute(stream, BASS_ATTRIB_VOL, 0));
        HSYNC sync = BASS_ChannelSetSync(stream, BASS_SYNC_END | BASS_SYNC_ONETIME, 0, streamEnded, NULL);
        r[@"endSyncInstalled"] = @(sync != 0);
        r[@"play"] = @(BASS_ChannelPlay(stream, FALSE));
        [NSThread sleepForTimeInterval:0.4];
        double before = BASS_ChannelBytes2Seconds(stream, BASS_ChannelGetPosition(stream, BASS_POS_BYTE));
        r[@"positionAdvanced"] = @(before > 0.05);
        r[@"pause"] = @(BASS_ChannelPause(stream) && BASS_ChannelIsActive(stream) == BASS_ACTIVE_PAUSED);
        double paused = BASS_ChannelBytes2Seconds(stream, BASS_ChannelGetPosition(stream, BASS_POS_BYTE));
        [NSThread sleepForTimeInterval:0.2];
        double after = BASS_ChannelBytes2Seconds(stream, BASS_ChannelGetPosition(stream, BASS_POS_BYTE));
        r[@"pausedPositionStable"] = @(fabs(after - paused) < 0.03);
        r[@"seek"] = @(BASS_ChannelSetPosition(stream, BASS_ChannelSeconds2Bytes(stream, 1.5), BASS_POS_BYTE));
        double sought = BASS_ChannelBytes2Seconds(stream, BASS_ChannelGetPosition(stream, BASS_POS_BYTE));
        r[@"seekPosition"] = @(fabs(sought - 1.5) < 0.05);
        r[@"resume"] = @(BASS_ChannelPlay(stream, FALSE));
        for (int i = 0; i < 50 && !ended; i++) [NSThread sleepForTimeInterval:0.1];
        r[@"endSyncDelivered"] = @(ended);
        r[@"streamFreed"] = @(BASS_StreamFree(stream));
    }
    if (flac) BASS_PluginFree(flac);
    if (opus) BASS_PluginFree(opus);
    r[@"freed"] = @(BASS_Free());
    [session setActive:NO error:nil];
    return r;
}

@interface ProbeSceneDelegate : UIResponder <UIWindowSceneDelegate>
@property(strong, nonatomic) UIWindow *window;
@end
@implementation ProbeSceneDelegate
- (void)scene:(UIScene *)scene willConnectToSession:(UISceneSession *)session options:(UISceneConnectionOptions *)options {
    self.window = [[UIWindow alloc] initWithWindowScene:(UIWindowScene *)scene];
    self.window.rootViewController = [UIViewController new];
    [self.window makeKeyAndVisible];
}
- (void)sceneDidBecomeActive:(UIScene *)scene {
    static dispatch_once_t once;
    dispatch_once(&once, ^{
    dispatch_async(dispatch_get_global_queue(QOS_CLASS_USER_INITIATED, 0), ^{
        @autoreleasepool {
            NSDictionary *result = probe();
            NSData *json = [NSJSONSerialization dataWithJSONObject:result options:NSJSONWritingPrettyPrinted error:nil];
            NSURL *directory = [NSFileManager.defaultManager URLsForDirectory:NSCachesDirectory inDomains:NSUserDomainMask].firstObject;
            [NSFileManager.defaultManager createDirectoryAtURL:directory withIntermediateDirectories:YES attributes:nil error:nil];
            BOOL written = [json writeToURL:[directory URLByAppendingPathComponent:@"result.json"] atomically:YES];
            NSLog(@"BASS probe result (written=%d): %@", written, result);
        }
    });
    });
}
@end
@interface ProbeDelegate : UIResponder <UIApplicationDelegate>
@end
@implementation ProbeDelegate
- (BOOL)application:(UIApplication *)application didFinishLaunchingWithOptions:(NSDictionary *)options { return YES; }
@end
int main(int argc, char **argv) {
    @autoreleasepool { return UIApplicationMain(argc, argv, nil, NSStringFromClass(ProbeDelegate.class)); }
}

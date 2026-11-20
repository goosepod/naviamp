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
    // Native availability/readback probe, matching the supported fields in Core's network policy.
    NSArray *network = @[
        @[@(BASS_CONFIG_NET_PLAYLIST), @1], @[@(BASS_CONFIG_NET_META), @1],
        @[@(BASS_CONFIG_NET_PLAYLIST_DEPTH), @5], @[@(BASS_CONFIG_NET_BUFFER), @5000],
        @[@(BASS_CONFIG_NET_PREBUF), @75], @[@(BASS_CONFIG_NET_TIMEOUT), @15000],
        @[@(BASS_CONFIG_NET_READTIMEOUT), @15000]
    ];
    BOOL networkConfigured = YES, networkReadBack = YES;
    NSMutableDictionary *networkResults = [NSMutableDictionary dictionary];
    for (NSArray *setting in network) {
        DWORD option = [setting[0] unsignedIntValue], value = [setting[1] unsignedIntValue];
        BOOL configured = BASS_SetConfig(option, value);
        int setError = configured ? 0 : BASS_ErrorGetCode();
        DWORD actual = BASS_GetConfig(option);
        networkConfigured &= configured;
        networkReadBack &= actual == value;
        networkResults[[NSString stringWithFormat:@"%u", option]] =
            @{@"configured": @(configured), @"setError": @(setError), @"value": @(actual)};
    }
    r[@"networkConfigured"] = @(networkConfigured);
    r[@"networkValuesReadBack"] = @(networkReadBack);
    r[@"networkResults"] = networkResults;
    NSString *resources = NSBundle.mainBundle.resourcePath;
    HPLUGIN flac = BASS_PluginLoad([NSBundle.mainBundle.privateFrameworksPath stringByAppendingPathComponent:@"bassflac.framework/bassflac"].UTF8String, 0);
    HPLUGIN opus = BASS_PluginLoad([NSBundle.mainBundle.privateFrameworksPath stringByAppendingPathComponent:@"bassopus.framework/bassopus"].UTF8String, 0);
    r[@"flacRegistered"] = @(flac != 0);
    r[@"opusRegistered"] = @(opus != 0);
    HSTREAM stream = BASS_StreamCreateFile(FALSE, [resources stringByAppendingPathComponent:@"fixture.wav"].UTF8String, 0, 0, BASS_SAMPLE_FLOAT);
    r[@"streamCreated"] = @(stream != 0);
    if (stream) {
        HFX equalizer = BASS_ChannelSetFX(stream, BASS_FX_DX8_PARAMEQ, 0);
        BASS_DX8_PARAMEQ parameters = {.fCenter = 1000, .fBandwidth = 12, .fGain = 3};
        BOOL equalizerApplied = equalizer && BASS_FXSetParameters(equalizer, &parameters);
        BOOL equalizerFreed = equalizer && BASS_ChannelRemoveFX(stream, equalizer);
        r[@"equalizerApplied"] = @(equalizerApplied && equalizerFreed);
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
    HSTREAM decoder = BASS_StreamCreateFile(FALSE, [resources stringByAppendingPathComponent:@"fixture.wav"].UTF8String,
                                            0, 0, BASS_SAMPLE_FLOAT | BASS_STREAM_DECODE);
    float rms = -1;
    BOOL rmsRead = decoder && BASS_ChannelGetLevelEx(decoder, &rms, 0.125, BASS_LEVEL_MONO | BASS_LEVEL_RMS);
    BOOL decoderFreed = decoder && BASS_StreamFree(decoder);
    r[@"waveformRmsRead"] = @(rmsRead && isfinite(rms) && rms == 0 && decoderFreed);
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

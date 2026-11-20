#import <UIKit/UIKit.h>
#import <AVFAudio/AVFAudio.h>

// Disposable simulator fixture. A separate nonmixing audio session produces real OS context objects.
@interface NaviampAudioInterrupterScene : UIResponder <UIWindowSceneDelegate>
@property(nonatomic, strong) UIWindow *window;
@property(nonatomic, strong) AVAudioPlayer *player;
@end
@implementation NaviampAudioInterrupterScene
- (void)scene:(UIScene *)scene willConnectToSession:(UISceneSession *)session options:(UISceneConnectionOptions *)options {
    self.window = [[UIWindow alloc] initWithWindowScene:(UIWindowScene *)scene];
    self.window.rootViewController = [UIViewController new];
    [self.window makeKeyAndVisible];
    for (NSString *name in @[AVAudioSessionDidBecomeActiveNotification, AVAudioSessionDidBecomeInactiveNotification, AVAudioSessionResumptionRecommendationNotification, AVAudioSessionInterruptionNotification]) {
        [NSNotificationCenter.defaultCenter addObserverForName:name object:nil queue:NSOperationQueue.mainQueue usingBlock:^(NSNotification *n) {
            printf("OS27 helper notification %s %s\n", n.name.UTF8String, n.userInfo.description.UTF8String); fflush(stdout);
        }];
    }
    NSError *error = nil;
    AVAudioSession *audio = AVAudioSession.sharedInstance;
    if (![audio setCategory:AVAudioSessionCategoryPlayback mode:AVAudioSessionModeDefault options:0 error:&error]) { NSLog(@"interrupter activation failed: %@", error); exit(2); }
    [audio activateWithOptions:0 completionHandler:^(BOOL activated, NSError *activationError) {
    dispatch_async(dispatch_get_main_queue(), ^{
    if (!activated) { NSLog(@"interrupter async activation failed: %@", activationError); exit(5); }
    NSError *error = nil;
    self.player = [[AVAudioPlayer alloc] initWithContentsOfURL:[NSBundle.mainBundle URLForResource:@"audio" withExtension:@"wav"] error:&error];
    self.player.volume = 0;
    self.player.numberOfLoops = -1;
    if (![self.player play]) { NSLog(@"interrupter playback failed: %@", error); exit(3); }
    puts("OS27 interrupter playing"); fflush(stdout);
    dispatch_after(dispatch_time(DISPATCH_TIME_NOW, 4 * NSEC_PER_SEC), dispatch_get_main_queue(), ^{
        puts("OS27 interrupter stopping"); fflush(stdout);
        [self.player stop];
        NSError *failure = nil;
        [audio deactivateWithOptions:AVAudioSessionDeactivationOptionNotifyOthersOnDeactivation completionHandler:^(BOOL stopped, NSError *failure) {
            if (!stopped) { NSLog(@"interrupter deactivation failed: %@", failure); exit(4); }
            puts("OS27 interrupter released"); fflush(stdout);
            dispatch_after(dispatch_time(DISPATCH_TIME_NOW, NSEC_PER_SEC), dispatch_get_main_queue(), ^{ exit(0); });
        }];
    });
    });
    }];
}
@end

@interface NaviampAudioInterrupterApp : UIResponder <UIApplicationDelegate>
@end
@implementation NaviampAudioInterrupterApp
@end

int main(int argc, char **argv) {
    @autoreleasepool { return UIApplicationMain(argc, argv, nil, NSStringFromClass(NaviampAudioInterrupterApp.class)); }
}

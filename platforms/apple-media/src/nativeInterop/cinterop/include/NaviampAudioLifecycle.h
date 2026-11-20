#import <Foundation/Foundation.h>
#import <AVFAudio/AVFAudio.h>
#import <Availability.h>
#import <TargetConditionals.h>

// Typed public SDK reads absent from Kotlin/Native's bundled platform klib. No playback decisions.
typedef NS_ENUM(NSInteger, NaviampAudioSource) {
    NaviampAudioSourceMissing = 0, NaviampAudioSourceApplication = 1,
    NaviampAudioSourceSystem = 2, NaviampAudioSourceUnknown = 3
};
typedef NS_ENUM(NSInteger, NaviampAudioRecommendation) {
    NaviampAudioRecommendationMissing = 0, NaviampAudioRecommendationResume = 1,
    NaviampAudioRecommendationStayPaused = 2, NaviampAudioRecommendationUnknown = 3
};
typedef NS_ENUM(NSInteger, NaviampAudioReason) {
    NaviampAudioReasonMissing = 0, NaviampAudioReasonOutputDisconnected = 1, NaviampAudioReasonOther = 2
};

// Xcode 26.6 can still build the CarPlay host. Its SDK lacks these declarations.
#if (TARGET_OS_IOS && __IPHONE_OS_VERSION_MAX_ALLOWED >= 270000) || \
    (TARGET_OS_TV && __TV_OS_VERSION_MAX_ALLOWED >= 270000)
static inline BOOL NaviampAudioLifecycleAvailable(void) {
    if (@available(iOS 27.0, tvOS 27.0, watchOS 27.0, visionOS 27.0, *)) return YES;
    return NO;
}
static inline NSString * _Nullable NaviampAudioActiveNotification(void) {
    if (@available(iOS 27.0, tvOS 27.0, watchOS 27.0, visionOS 27.0, *)) return AVAudioSessionDidBecomeActiveNotification;
    return nil;
}
static inline NSString * _Nullable NaviampAudioInactiveNotification(void) {
    if (@available(iOS 27.0, tvOS 27.0, watchOS 27.0, visionOS 27.0, *)) return AVAudioSessionDidBecomeInactiveNotification;
    return nil;
}
static inline NSString * _Nullable NaviampAudioResumptionNotification(void) {
    if (@available(iOS 27.0, tvOS 27.0, watchOS 27.0, visionOS 27.0, *)) return AVAudioSessionResumptionRecommendationNotification;
    return nil;
}
static inline NaviampAudioSource NaviampAudioReadSource(NSNotification * _Nonnull notification) {
    if (@available(iOS 27.0, tvOS 27.0, watchOS 27.0, visionOS 27.0, *)) {
        id value = notification.userInfo[AVAudioSessionDeactivationContextKey];
        if (![value isKindOfClass:AVAudioSessionDeactivationContext.class]) return NaviampAudioSourceMissing;
        switch (((AVAudioSessionDeactivationContext *)value).source) {
            case AVAudioSessionDeactivationSourceApp: return NaviampAudioSourceApplication;
            case AVAudioSessionDeactivationSourceSystem: return NaviampAudioSourceSystem;
            default: return NaviampAudioSourceUnknown;
        }
    }
    return NaviampAudioSourceMissing;
}
static inline NaviampAudioReason NaviampAudioReadReason(NSNotification * _Nonnull notification) {
    if (@available(iOS 27.0, tvOS 27.0, watchOS 27.0, visionOS 27.0, *)) {
        id value = notification.userInfo[AVAudioSessionDeactivationContextKey];
        if (![value isKindOfClass:AVAudioSessionDeactivationContext.class]) return NaviampAudioReasonMissing;
        AVAudioSessionInterruptionContext *context = ((AVAudioSessionDeactivationContext *)value).interruptionContext;
        if (!context) return NaviampAudioReasonMissing;
        return context.reason == AVAudioSessionInterruptionReasonRouteDisconnected ? NaviampAudioReasonOutputDisconnected : NaviampAudioReasonOther;
    }
    return NaviampAudioReasonMissing;
}
static inline NaviampAudioRecommendation NaviampAudioReadRecommendation(NSNotification * _Nonnull notification) {
    if (@available(iOS 27.0, tvOS 27.0, watchOS 27.0, visionOS 27.0, *)) {
        id value = notification.userInfo[AVAudioSessionResumptionContextKey];
        if (![value isKindOfClass:AVAudioSessionResumptionContext.class]) return NaviampAudioRecommendationMissing;
        switch (((AVAudioSessionResumptionContext *)value).recommendation) {
            case AVAudioSessionResumptionRecommendationShouldResume: return NaviampAudioRecommendationResume;
            case AVAudioSessionResumptionRecommendationShouldNotResume: return NaviampAudioRecommendationStayPaused;
            default: return NaviampAudioRecommendationUnknown;
        }
    }
    return NaviampAudioRecommendationMissing;
}
#else
static inline BOOL NaviampAudioLifecycleAvailable(void) { return NO; }
static inline NSString * _Nullable NaviampAudioActiveNotification(void) { return nil; }
static inline NSString * _Nullable NaviampAudioInactiveNotification(void) { return nil; }
static inline NSString * _Nullable NaviampAudioResumptionNotification(void) { return nil; }
static inline NaviampAudioSource NaviampAudioReadSource(NSNotification * _Nonnull notification) {
    (void)notification;
    return NaviampAudioSourceMissing;
}
static inline NaviampAudioReason NaviampAudioReadReason(NSNotification * _Nonnull notification) {
    (void)notification;
    return NaviampAudioReasonMissing;
}
static inline NaviampAudioRecommendation NaviampAudioReadRecommendation(NSNotification * _Nonnull notification) {
    (void)notification;
    return NaviampAudioRecommendationMissing;
}
#endif

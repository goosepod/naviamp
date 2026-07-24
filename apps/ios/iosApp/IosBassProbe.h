#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

FOUNDATION_EXPORT uint32_t NaviampBassProbeVersion(void);
FOUNDATION_EXPORT BOOL NaviampBassProbeInitialize(void);
FOUNDATION_EXPORT int32_t NaviampBassProbeLastError(void);
FOUNDATION_EXPORT void NaviampBassProbeFree(void);

NS_ASSUME_NONNULL_END

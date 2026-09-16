#include <jni.h>
#include <array>
#include <jawt.h>
#include <jawt_md.h>
#import <AppKit/AppKit.h>
#import <QuartzCore/QuartzCore.h>
struct Host { __strong id<JAWT_SurfaceLayers> surface; __strong CALayer *root; };
extern "C" JNIEXPORT jlong JNICALL Java_app_naviamp_ui_ProbeCompositor_create(JNIEnv* env, jobject, jobject component) {
 JAWT awt{}; awt.version=JAWT_VERSION_9;
 if (!JAWT_GetAWT(env,&awt)) return 0;
 auto ds=awt.GetDrawingSurface(env,component); if(!ds) return 0;
 Host* host=nullptr;
 if(!(ds->Lock(ds)&JAWT_LOCK_ERROR)) {
  auto info=ds->GetDrawingSurfaceInfo(ds);
  if(info && info->platformInfo) {
   host=new Host(); host->surface=(__bridge id<JAWT_SurfaceLayers>)info->platformInfo;
   dispatch_sync(dispatch_get_main_queue(), ^{
    host->root=[CALayer layer]; host->root.masksToBounds=YES;
    host->root.backgroundColor=[NSColor colorWithRed:0.141 green:0.141 blue:0.169 alpha:1].CGColor;
    host->surface.layer=host->root;
   });
  }
  if(info) ds->FreeDrawingSurfaceInfo(info); ds->Unlock(ds);
 }
 awt.FreeDrawingSurface(ds); return (jlong)host;
}
extern "C" JNIEXPORT void JNICALL Java_app_naviamp_ui_ProbeCompositor_layer(JNIEnv* env,jobject,jlong ptr,jbyteArray bytes,jdouble x,jdouble y,jdouble width,jdouble height,jstring key,jdoubleArray values,jdoubleArray times,jdouble duration,jboolean repeat) {
 auto host=(Host*)ptr; if(!host)return;
 NSData* data=nil;
 if(bytes){ auto n=env->GetArrayLength(bytes); auto p=env->GetByteArrayElements(bytes,nullptr);data=[NSData dataWithBytes:p length:n];env->ReleaseByteArrayElements(bytes,p,JNI_ABORT); }
 const char* chars=env->GetStringUTFChars(key,nullptr);NSString* property=[NSString stringWithUTF8String:chars];env->ReleaseStringUTFChars(key,chars);
 auto n=env->GetArrayLength(values);auto v=env->GetDoubleArrayElements(values,nullptr);auto t=env->GetDoubleArrayElements(times,nullptr);
 NSMutableArray* vs=[NSMutableArray array];NSMutableArray* ts=[NSMutableArray array];for(int i=0;i<n;i++){[vs addObject:@(v[i])];[ts addObject:@(t[i])];}
 env->ReleaseDoubleArrayElements(values,v,JNI_ABORT);env->ReleaseDoubleArrayElements(times,t,JNI_ABORT);
 dispatch_sync(dispatch_get_main_queue(), ^{
  [CATransaction begin];[CATransaction setDisableActions:YES];
  CALayer* layer=[CALayer layer];layer.anchorPoint=CGPointZero;layer.position=CGPointMake(x,y);layer.bounds=CGRectMake(0,0,width,height);
  if(data){
   NSBitmapImageRep* rep=[NSBitmapImageRep imageRepWithData:data];
   if([property isEqualToString:@"bounds.size.width"]) {
    CALayer* pixels=[CALayer layer];pixels.anchorPoint=CGPointZero;pixels.position=CGPointZero;pixels.bounds=CGRectMake(0,0,width,height);pixels.contents=(__bridge id)rep.CGImage;
    layer.masksToBounds=YES;[layer addSublayer:pixels];
    if(n>0)layer.bounds=CGRectMake(0,0,[vs[0] doubleValue],height);
   } else layer.contents=(__bridge id)rep.CGImage;
  }else layer.backgroundColor=NSColor.whiteColor.CGColor;
  [host->root addSublayer:layer];
  if(n>1){CAKeyframeAnimation* a=[CAKeyframeAnimation animationWithKeyPath:property];a.values=vs;a.keyTimes=ts;a.duration=duration;a.repeatCount=repeat?HUGE_VALF:0;a.removedOnCompletion=NO;a.fillMode=kCAFillModeForwards;[layer addAnimation:a forKey:@"motion"];}
  [CATransaction commit];
 });
}
extern "C" JNIEXPORT void JNICALL Java_app_naviamp_ui_ProbeCompositor_clear(JNIEnv*,jobject,jlong ptr) {auto h=(Host*)ptr;if(h)dispatch_sync(dispatch_get_main_queue(), ^{h->root.sublayers=nil;});}
extern "C" JNIEXPORT void JNICALL Java_app_naviamp_ui_ProbeCompositor_dispose(JNIEnv*,jobject,jlong ptr) {auto h=(Host*)ptr;if(h){dispatch_sync(dispatch_get_main_queue(), ^{h->surface.layer=nil;});delete h;}}
extern "C" JNIEXPORT jdoubleArray JNICALL Java_app_naviamp_ui_ProbeCompositor_positions(JNIEnv* env,jobject,jlong ptr) {
 auto h=(Host*)ptr; __block std::array<double,6> values{};
 if(h)dispatch_sync(dispatch_get_main_queue(), ^{
  auto layers=h->root.sublayers;
  values[0]=h->root.bounds.size.width;values[1]=h->root.bounds.size.height;
  for(NSUInteger i=0;i<MIN((NSUInteger)3,layers.count);i++){CALayer* p=layers[i].presentationLayer;values[2+i]=p.transform.m41;}
  if(layers.count>3)values[5]=((CALayer*)layers.lastObject.presentationLayer).bounds.size.width;
 });
 auto result=env->NewDoubleArray(6);env->SetDoubleArrayRegion(result,0,6,values.data());return result;
}

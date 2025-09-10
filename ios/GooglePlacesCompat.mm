#import <React/RCTBridgeModule.h>

#ifdef RCT_NEW_ARCH_ENABLED
#import "RNGooglePlacesCompatSpec.h"
#endif

@interface RCT_EXTERN_MODULE(RNGooglePlaces, NSObject)

RCT_EXTERN_METHOD(initializePlaceClient:(NSString *)apiKey
                  sessionBasedAutocomplete:(BOOL)sessionBasedAutocomplete)

RCT_EXTERN_METHOD(openAutocompleteModal:(NSDictionary *)options
                  placeFields:(NSArray *)placeFields
                  resolve:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(getAutocompletePredictions:(NSString *)query
                  options:(NSDictionary *)options
                  resolve:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(lookUpPlaceByID:(NSString *)placeID
                  placeFields:(NSArray *)placeFields
                  resolve:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(getCurrentPlace:(NSArray *)placeFields
                  resolve:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(setSessionBasedAutocomplete:(BOOL)enabled)

RCT_EXTERN_METHOD(refreshSessionToken)


+ (BOOL)requiresMainQueueSetup
{
  return NO;
}

@end

#ifdef RCT_NEW_ARCH_ENABLED
@interface RNGooglePlaces () <NativeGooglePlacesCompatSpec>
@end
#endif

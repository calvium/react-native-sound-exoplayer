#import "RNSound.h"
#import "RCTUtils.h"

@implementation RNSound {
  NSMutableDictionary* _playerPool;
  NSMutableDictionary* _callbackPool;
  NSMutableDictionary* _interruptionsPool;
}

-(NSMutableDictionary*) playerPool {
  if (!_playerPool) {
    _playerPool = [NSMutableDictionary new];
  }
  return _playerPool;
}

-(NSMutableDictionary*) callbackPool {
  if (!_callbackPool) {
    _callbackPool = [NSMutableDictionary new];
  }
  return _callbackPool;
}

-(NSMutableDictionary*) interruptionsPool {
    if (!_interruptionsPool) {
        _interruptionsPool = [NSMutableDictionary new];
    }
    return _interruptionsPool;
}

-(AVAudioPlayer*) playerForKey:(nonnull NSNumber*)key {
  return [[self playerPool] objectForKey:key];
}

-(NSNumber*) keyForPlayer:(nonnull AVAudioPlayer*)player {
  return [[[self playerPool] allKeysForObject:player] firstObject];
}

-(RCTResponseSenderBlock) callbackForKey:(nonnull NSNumber*)key {
  return [[self callbackPool] objectForKey:key];
}

-(NSString *) getDirectory:(int)directory {
  return [NSSearchPathForDirectoriesInDomains(directory, NSUserDomainMask, YES) firstObject];
}

#pragma mark Handle Interruptions

-(void)audioPlayerBeginInterruption:(AVAudioPlayer *)player
{
    // Remember that this player was interrupted...
    NSLog(@"-- interrupted --");
    NSNumber* key = [self keyForPlayer:player];
    if (!key) {
        return;
    }
    [[self interruptionsPool] setObject:@true forKey:key];
}


- (void)audioPlayerEndInterruption:(AVAudioPlayer *)player
{
    // Was this player paused because it was interrupted?
    NSNumber* key = [self keyForPlayer:player];
    if (!key) {
        return;
    }
    NSNumber* wasInterrupted = [[self interruptionsPool] objectForKey:key];
    if (!wasInterrupted || ![wasInterrupted boolValue]) {
        return;
    }
    [[self interruptionsPool] removeObjectForKey:key];
    
    NSLog(@"resume!");
    
    // Wait a short while, then resume
    dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(1 * NSEC_PER_SEC)), dispatch_get_main_queue(), ^{
        [player prepareToPlay];
        [player play];
    });
}

#pragma Audio System

-(void) audioPlayerDidFinishPlaying:(AVAudioPlayer*)player
                       successfully:(BOOL)flag {
  NSNumber* key = [self keyForPlayer:player];
  if (key != nil) {
    RCTResponseSenderBlock callback = [self callbackForKey:key];
    if (callback) {
      callback(@[@(flag)]);
    }
  }
}

RCT_EXPORT_MODULE();

-(NSDictionary *)constantsToExport {
  return @{@"IsAndroid": [NSNumber numberWithBool:NO],
           @"MainBundlePath": [[NSBundle mainBundle] bundlePath],
           @"NSDocumentDirectory": [self getDirectory:NSDocumentDirectory],
           @"NSLibraryDirectory": [self getDirectory:NSLibraryDirectory],
           @"NSCachesDirectory": [self getDirectory:NSCachesDirectory],
           };
}

RCT_EXPORT_METHOD(enable:(BOOL)enabled) {
  AVAudioSession *session = [AVAudioSession sharedInstance];
  [session setCategory: AVAudioSessionCategoryAmbient error: nil];
  [session setActive: enabled error: nil];
}

RCT_EXPORT_METHOD(setCategory:(nonnull NSNumber*)key withValue:(NSString*)categoryName) {
  AVAudioSession *session = [AVAudioSession sharedInstance];
  if ([categoryName isEqual: @"Ambient"]) {
    [session setCategory: AVAudioSessionCategoryAmbient error: nil];
  } else if ([categoryName isEqual: @"SoloAmbient"]) {
    [session setCategory: AVAudioSessionCategorySoloAmbient error: nil];
  } else if ([categoryName isEqual: @"Playback"]) {
    [session setCategory: AVAudioSessionCategoryPlayback error: nil];
  } else if ([categoryName isEqual: @"Record"]) {
    [session setCategory: AVAudioSessionCategoryRecord error: nil];
  } else if ([categoryName isEqual: @"PlayAndRecord"]) {
    [session setCategory: AVAudioSessionCategoryPlayAndRecord error: nil];
  } else if ([categoryName isEqual: @"AudioProcessing"]) {
    [session setCategory: AVAudioSessionCategoryAudioProcessing error: nil];
  } else if ([categoryName isEqual: @"MultiRoute"]) {
    [session setCategory: AVAudioSessionCategoryMultiRoute error: nil];
  }
}

RCT_EXPORT_METHOD(enableInSilenceMode:(BOOL)enabled) {
  AVAudioSession *session = [AVAudioSession sharedInstance];
  [session setCategory: AVAudioSessionCategoryPlayback error: nil];
  [session setActive: enabled error: nil];
}

RCT_EXPORT_METHOD(prepare:(NSString*)fileName withKey:(nonnull NSNumber*)key
                  withCallback:(RCTResponseSenderBlock)callback) {
  NSError* error;
  AVAudioPlayer* player = [[AVAudioPlayer alloc]
                           initWithContentsOfURL:[NSURL fileURLWithPath:[fileName stringByRemovingPercentEncoding]]
                           error:&error];
  if (player) {
    player.delegate = self;
    [player prepareToPlay];
    [[self playerPool] setObject:player forKey:key];
    callback(@[[NSNull null], @{@"duration": @(player.duration),
                                @"numberOfChannels": @(player.numberOfChannels)}]);
  } else {
    callback(@[RCTJSErrorFromNSError(error)]);
  }
}

RCT_EXPORT_METHOD(play:(nonnull NSNumber*)key withCallback:(RCTResponseSenderBlock)callback) {
  AVAudioPlayer* player = [self playerForKey:key];
  if (player) {
    [[self callbackPool] setObject:[callback copy] forKey:key];
    [player play];
  }
}

RCT_EXPORT_METHOD(pause:(nonnull NSNumber*)key) {
  AVAudioPlayer* player = [self playerForKey:key];
  if (player) {
    [player pause];
  }
}

RCT_EXPORT_METHOD(stop:(nonnull NSNumber*)key) {
  AVAudioPlayer* player = [self playerForKey:key];
  if (player) {
    [player stop];
    player.currentTime = 0;
  }
}

RCT_EXPORT_METHOD(release:(nonnull NSNumber*)key) {
  AVAudioPlayer* player = [self playerForKey:key];
  if (player) {
    [player stop];
    [[self callbackPool] removeObjectForKey:player];
    [[self playerPool] removeObjectForKey:key];
  }
}

RCT_EXPORT_METHOD(setVolume:(nonnull NSNumber*)key withValue:(nonnull NSNumber*)value) {
  AVAudioPlayer* player = [self playerForKey:key];
  if (player) {
    player.volume = [value floatValue];
  }
}

RCT_EXPORT_METHOD(setPan:(nonnull NSNumber*)key withValue:(nonnull NSNumber*)value) {
  AVAudioPlayer* player = [self playerForKey:key];
  if (player) {
    player.pan = [value floatValue];
  }
}

RCT_EXPORT_METHOD(setNumberOfLoops:(nonnull NSNumber*)key withValue:(nonnull NSNumber*)value) {
  AVAudioPlayer* player = [self playerForKey:key];
  if (player) {
    player.numberOfLoops = [value intValue];
  }
}

RCT_EXPORT_METHOD(setCurrentTime:(nonnull NSNumber*)key withValue:(nonnull NSNumber*)value) {
  AVAudioPlayer* player = [self playerForKey:key];
  if (player) {
    player.currentTime = [value doubleValue];
  }
}

RCT_EXPORT_METHOD(getCurrentTime:(nonnull NSNumber*)key
                  withCallback:(RCTResponseSenderBlock)callback) {
  AVAudioPlayer* player = [self playerForKey:key];
  if (player) {
    callback(@[@(player.currentTime), @(player.isPlaying)]);
  } else {
    callback(@[@(-1), @(false)]);
  }
}

@end

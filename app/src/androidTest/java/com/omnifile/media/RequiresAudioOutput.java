package com.omnifile.media;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a test that needs the emulator's audio output to actually run a clip
 * to completion, not merely to start.
 *
 * This is a stricter bar than {@link RequiresAudioClock}. A headless CI
 * emulator can usually bring playback up but cannot be relied on to keep the
 * output clock moving: wavPlaysToEndedState was observed stuck in BUFFERING on
 * API 31 and on API 32, in both cases with audio focus granted and the
 * AudioFlinger output thread active, and in both cases with a wait bound far
 * longer than the two second clip needs. The stall is in the device's audio
 * output, not in the code under test.
 *
 * Like RequiresAudioClock this annotation is inert unless a run passes the
 * runner argument
 * {@code -e notAnnotation com.omnifile.media.RequiresAudioOutput}, which the
 * emulator workflow does for every API level. The test still runs in the
 * physical-device acceptance process, where a real output exists.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RequiresAudioOutput {}

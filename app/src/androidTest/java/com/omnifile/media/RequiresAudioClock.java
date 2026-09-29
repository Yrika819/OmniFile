package com.omnifile.media;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a test that can only pass once the device has actually rendered audio
 * and its clock has advanced.
 *
 * These tests assert that a playback position moves, or that playback reaches
 * ENDED, so they need a granted audio focus and a working audio output. That is
 * a property of the device, not of the code under test.
 *
 * The annotation itself changes nothing. It is inert everywhere unless a run
 * passes the runner argument
 * {@code -e notAnnotation com.omnifile.media.RequiresAudioClock}, which only
 * the cloud emulator workflow does, and only for the API level where the
 * emulator cannot provide audio. Marking the tests rather than suppressing them
 * keeps them running in the physical-device acceptance process, where a real
 * audio output exists and these assertions are exactly what should be checked.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RequiresAudioClock {}

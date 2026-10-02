package com.omnifile.preview

/**
 * Marks a class whose cases belong to the targeted mandatory re-run campaign, which a single
 * runner `annotation` argument selects.
 *
 * Inert in complete suites and in the product: it carries no runtime behaviour and cannot be
 * reached from product input. Its name is historical. What it actually means is "this case is
 * mandatory evidence, so a narrowed re-run must still execute and prove it" — which is why the
 * VS11 File Details cases carry it alongside the VS10 and post-VS10 ones.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class PostVs10HardeningTarget

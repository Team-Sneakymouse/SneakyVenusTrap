package com.sneakyvenustrap.service

/** Canonical upper/lower share the (0,0,0.625) hinge; never cross through zero.
 * Opposing canonical fang cuboids still overlap at 13 degrees; 14 is the first
 * safe whole-degree stop (see canonical-jaw-bounds.csv and JawClosureTest).
 * This is tooth-tip closure, not a claim that the fleshy lips can seal with this asset.
 */
object JawGeometry {
    const val CLOSED_ANGLE = 14f
}

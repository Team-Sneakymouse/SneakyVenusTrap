package com.sneakyvenustrap.encounter

import org.bukkit.damage.DamageSource
import org.bukkit.damage.DamageType
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent.DamageCause

/**
 * Standard cancellable damage authorization for custom encounter combat, not physical entity damage.
 * Ordinary EntityDamageByEntityEvent protection listeners receive this event through its inherited
 * handler list. Cancellation or non-positive final damage denies ALL combat effects, including help
 * and stomach puzzle strikes. Positive damage modifications also apply to shared garden health.
 */
class EncounterAttackAuthorizationEvent(player: Player, target: Entity) : EntityDamageByEntityEvent(
    player, target, DamageCause.ENTITY_ATTACK,
    DamageSource.builder(DamageType.PLAYER_ATTACK).withCausingEntity(player).withDirectEntity(player).build(),
    10.0,
)

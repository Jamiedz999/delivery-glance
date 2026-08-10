package com.deliveryglance.identityaccess;

import java.util.UUID;

/**
 * The signed-in Internal Account as other modules are allowed to see it: enough to record who
 * caused a change, and nothing else about the account.
 */
public record CurrentActor(UUID accountId, String displayName, InternalAccountRole role) {
}

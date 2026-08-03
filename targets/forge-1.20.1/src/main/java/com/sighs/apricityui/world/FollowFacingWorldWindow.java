package com.sighs.apricityui.world;

import net.minecraft.world.phys.Vec3;

/**
 * @deprecated Configure {@link WorldWindow#setFollow(boolean)},
 *             {@link WorldWindow#setFollowFactor(float)} and
 *             {@link WorldWindow#setFacing(boolean)} directly on a
 *             {@link WorldWindow} instead.
 */
@Deprecated
public class FollowFacingWorldWindow extends WorldWindow {
    /**
     * @deprecated Use {@link WorldWindow} and configure follow/facing explicitly.
     */
    @Deprecated
    public FollowFacingWorldWindow(String documentPath, Vec3 position, int maxDistance, float followFactor) {
        super(documentPath, position, maxDistance);
        configureLegacyBehavior(followFactor);
    }

    /**
     * @deprecated Use the viewport defined by the document meta tag and configure
     *             follow/facing on {@link WorldWindow}.
     */
    @Deprecated
    public FollowFacingWorldWindow(String documentPath, Vec3 position, float width, float height,
                                   int maxDistance, float followFactor) {
        super(documentPath, position, width, height, maxDistance);
        configureLegacyBehavior(followFactor);
    }

    private void configureLegacyBehavior(float followFactor) {
        setFollow(true);
        setFollowFactor(followFactor);
        setFacing(true);
    }
}

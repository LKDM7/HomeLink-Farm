package fr.lkdm.homelink.farm.farm.controller;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;

/**
 * A block entity that can be grouped under a Farm Controller. Linking goes through
 * {@link FarmLinkService}, so any connector tool (the Farm Connector today, a universal
 * HomeLink connector later) can reuse the same validated server logic.
 */
public interface FarmComponent {
    UUID componentId();

    FarmComponentKind componentKind();

    BlockPos componentPos();

    Optional<ControllerLink> controllerLink();

    void setControllerLink(ControllerLink link);

    void clearControllerLink();
}

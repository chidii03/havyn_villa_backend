package com.havyn.admin.web;

import com.havyn.properties.rayprop.RayPropSyncResult;
import com.havyn.properties.rayprop.RayPropSyncService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Manual trigger for the RayProp wholesale-inventory import — on-demand rather than
 * scheduled so the first sync can be verified before automating it (see
 * {@code properties.rayprop.RayPropSyncService}).
 */
@RestController
@RequestMapping("/api/v1/admin/rayprop")
@PreAuthorize("hasRole('ADMIN')")
public class AdminRayPropController {

    private final RayPropSyncService rayPropSyncService;

    public AdminRayPropController(RayPropSyncService rayPropSyncService) {
        this.rayPropSyncService = rayPropSyncService;
    }

    @PostMapping("/sync")
    public RayPropSyncResult sync() {
        return rayPropSyncService.sync();
    }
}

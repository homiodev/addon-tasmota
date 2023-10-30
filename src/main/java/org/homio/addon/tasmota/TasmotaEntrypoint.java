package org.homio.addon.tasmota;

import static org.homio.api.entity.HasJsonData.LIST_DELIMITER;
import static org.homio.api.util.Constants.PRIMARY_DEVICE;

import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.homio.api.AddonEntrypoint;
import org.homio.api.Context;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class TasmotaEntrypoint implements AddonEntrypoint {

    public static final String TASMOTA_ICON = "fas fa-house-signal";
    public static final String TASMOTA_COLOR = "#2899ED";

    private final Context context;

    @Override
    public void init() {
        ensureEntityExists(context);
        context.setting().listenValue(TasmotaCompactModeSetting.class, "tasmota-compact-mode",
            (value) -> context.ui().updateItems(TasmotaDeviceEntity.class));
    }

    public void ensureEntityExists(Context context) {
        TasmotaProjectEntity entity = context.db().getEntity(TasmotaProjectEntity.class, PRIMARY_DEVICE);
        if (entity == null) {
            entity = new TasmotaProjectEntity();
            entity.setEntityID(PRIMARY_DEVICE);
            entity.setName("Tasmota");
            entity.setPatterns(String.join(LIST_DELIMITER, Set.of("%prefix%/%topic%/", "%topic%/%prefix%/")));
            entity.setMqttEntity(context.service().getPrimaryMqttEntity());
            context.db().save(entity, false);
        }
    }
}

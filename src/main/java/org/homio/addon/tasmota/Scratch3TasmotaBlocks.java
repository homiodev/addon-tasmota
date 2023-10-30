package org.homio.addon.tasmota;

import static org.homio.addon.tasmota.TasmotaEntrypoint.TASMOTA_COLOR;

import lombok.Getter;
import org.homio.api.Context;
import org.homio.api.workspace.scratch.Scratch3BaseDeviceBlocks;
import org.springframework.stereotype.Component;

@Getter
@Component
public class Scratch3TasmotaBlocks extends Scratch3BaseDeviceBlocks {

    public Scratch3TasmotaBlocks(Context context, TasmotaEntrypoint TasmotaEntrypoint) {
        super(TASMOTA_COLOR, context, TasmotaEntrypoint, TasmotaDeviceEntity.PREFIX);
    }
}

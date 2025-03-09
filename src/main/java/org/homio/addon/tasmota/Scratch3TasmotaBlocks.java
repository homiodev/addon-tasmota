package org.homio.addon.tasmota;

import lombok.Getter;
import org.homio.api.Context;
import org.homio.api.workspace.scratch.Scratch3BaseDeviceBlocks;
import org.springframework.stereotype.Component;

import static org.homio.addon.tasmota.TasmotaEntrypoint.TASMOTA_COLOR;

@Getter
@Component
public class Scratch3TasmotaBlocks extends Scratch3BaseDeviceBlocks {

  public Scratch3TasmotaBlocks(Context context, TasmotaEntrypoint entrypoint) {
    super(TASMOTA_COLOR, context, entrypoint, TasmotaDeviceEntity.PREFIX);
  }
}

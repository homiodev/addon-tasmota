package org.homio.addon.tasmota;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.homio.api.model.Icon;
import org.homio.api.model.device.ConfigDeviceEndpoint;
import org.homio.api.model.endpoint.BaseDeviceEndpoint;
import org.homio.api.state.State;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Function;

@Log4j2
@Getter
public class TasmotaEndpoint extends BaseDeviceEndpoint<TasmotaDeviceEntity> {

  @Setter
  private @Nullable Function<JsonNode, State> dataReader;

  public TasmotaEndpoint(@NotNull String endpointEntityID,
                         @NotNull EndpointType endpointType,
                         @NotNull TasmotaDeviceEntity device) {
    this(endpointEntityID, endpointType, device, builder -> {
    });
  }

  public TasmotaEndpoint(@NotNull String endpointEntityID,
                         @NotNull EndpointType endpointType,
                         @NotNull TasmotaDeviceEntity device,
                         @NotNull Consumer<TasmotaEndpoint> builder) {
    super("TASMOTA", device.context());
    ConfigDeviceEndpoint configEndpoint = TasmotaDeviceService.CONFIG_DEVICE_SERVICE.getDeviceEndpoints().get(endpointEntityID);

    setIcon(new Icon(
      "fa fa-fw fa-" + (configEndpoint == null ? "tablet-screen-button" : configEndpoint.getIcon()),
      configEndpoint == null ? "#3894B5" : configEndpoint.getIconColor()));

    init(
      TasmotaDeviceService.CONFIG_DEVICE_SERVICE,
      endpointEntityID,
      device,
      endpointEntityID,
      endpointType);

    builder.accept(this);

    getOrCreateVariable();
  }

  public void mqttUpdate(JsonNode payload) {
    if (dataReader != null) {
      State state = dataReader.apply(payload);
      if (state != null) {
        this.setValue(state, true);
      }
    }
  }

  @Override
  public void writeValue(@NotNull State state) {
        /*switch (expose.getType()) {
            case NUMBER_TYPE -> fireAction(state.intValue());
            case BINARY_TYPE, SWITCH_TYPE -> fireAction(state.boolValue());
            default -> fireAction(state.stringValue());
        }*/
  }

  @Override
  public void readValue() {
  }

  @Override
  public String getVariableGroupID() {
    return "tasmota-" + getDeviceID();
  }
}

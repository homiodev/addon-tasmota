package org.homio.addon.tasmota;

import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.homio.api.Context;
import org.homio.api.ContextService.MQTTEntityService;
import org.homio.api.model.Icon;
import org.homio.api.service.EntityService.ServiceInstance;
import org.homio.api.state.DecimalType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.homio.addon.tasmota.TasmotaEntrypoint.TASMOTA_COLOR;
import static org.homio.addon.tasmota.TasmotaEntrypoint.TASMOTA_ICON;
import static org.homio.api.model.endpoint.DeviceEndpoint.ENDPOINT_LAST_SEEN;

@Getter
public class TasmotaProjectService extends ServiceInstance<TasmotaProjectEntity> {

  private static final Map<String, String> INITIAL_COMMANDS = new HashMap<>(
    Map.of("status", "0", "template", "", "modules", "", "gpio", "", "gpios", "255",
      "buttondebounce", "", "switchdebounce", "", "interlock", "", "blinktime", "",
      "blinkcount", "")
  );
  public static TasmotaProjectService INSTANCE;

  static {
    INITIAL_COMMANDS.put("mqttlog", "");
    for (int pt = 0; pt < 8; pt++) {
      INITIAL_COMMANDS.put("pulsetime" + (pt + 1), "");
    }

    for (int sht = 0; sht < 4; sht++) {
      INITIAL_COMMANDS.put("shutterrelay" + (sht + 1), "");
      INITIAL_COMMANDS.put("shutterposition" + (sht + 1), "");
    }
  }

  private final Set<String> lwts = new ConcurrentSkipListSet<>();
  private MQTTEntityService mqttEntityService;
  private Map<String, TasmotaDeviceEntity> existedDevices = new HashMap<>();

  public TasmotaProjectService(@NotNull Context context, @NotNull TasmotaProjectEntity entity) {
    super(context, entity, true, "Tasmota");
    INSTANCE = this;
  }

  public static String cmndTopic(TasmotaDeviceEntity entity, @Nullable String command) {
    if (StringUtils.isNotEmpty(command)) {
      return build_topic(entity, "cmnd") + "/" + command;
    }
    return build_topic(entity, "cmnd");
  }

  public static @Nullable ParsedTopic parseTopic(String fullTopic, String topic) {
    Pattern pattern = Pattern.compile(fullTopic
                                        .replace("%topic%", "(.*?)")
                                        .replace("%prefix%", "(.*?)") + "(.*)$");
    Matcher matcher = pattern.matcher(topic);
    if (matcher.matches()) {
      return new ParsedTopic(matcher.group(1), matcher.group(2), matcher.group(3));
    }
    return null;
  }

  private static String build_topic(TasmotaDeviceEntity entity, String prefix) {
    return entity.getFullTopic()
      .replace("%prefix%", prefix)
      .replace("%topic%", entity.getIeeeAddress())
      .replaceAll("/+$", "");
  }

  public void dispose(@Nullable Exception ignore) {
    updateNotificationBlock();
  }

  public void publish(TasmotaDeviceEntity entity, String key, String value) {
    String command = cmndTopic(entity, key);
    mqttEntityService.publish(command, value.getBytes());
  }

  public String tele_topic(TasmotaDeviceEntity entity, String endpoint) {
    if (StringUtils.isNotEmpty(endpoint)) {
      return build_topic(entity, "tele") + "/" + endpoint;
    }
    return build_topic(entity, "tele");
  }

  public void initialQuery(TasmotaDeviceEntity device) {
    for (Entry<String, String> command : INITIAL_COMMANDS.entrySet()) {
      String cmd = cmndTopic(device, command.getKey());
      mqttEntityService.publish(cmd, command.getValue().getBytes());
    }
  }

  @Override
  public void destroy(boolean forRestart, Exception ex) {
    this.dispose(null);
  }

  @Override
  protected void initialize() {
    entity.setStatusOnline();
  }

  @Override
  protected void firstInitialize() {
    context.var().createGroup("tasmota", "Tasmota", builder ->
      builder.setLocked(true).setIcon(new Icon(TASMOTA_ICON, TASMOTA_COLOR)));

    mqttEntityService = entity.getMqttEntityService();
    existedDevices = context.db().findAll(TasmotaDeviceEntity.class)
      .stream()
      .collect(Collectors.toMap(TasmotaDeviceEntity::getIeeeAddress, t -> t));
    mqttEntityService.addPayloadListener(Set.of("tele/#", "stat/#", "cmnd/#", "+/tele/#", "+/stat/#", "+/cmnd/#"),
      "tasmota", entityID, log, (topic, payload) -> {
        MatchDeviceData data = findDevice(topic);
        if (data != null) {
          data.entity.getService().getEndpoints().get(ENDPOINT_LAST_SEEN)
            .setValue(new DecimalType(System.currentTimeMillis()), true);

          if (topic.endsWith("LWT")) {
            String msg = payload.get("raw").asText("Offline");
            data.entity.getService().put("LWT", msg);
            if ("Online".equals(msg)) {
              initialQuery(data.entity);
            }
          } else {
            // forward the message for processing
            data.entity.getService().mqttUpdate(payload, data);
          }
        }
        if (topic.endsWith("LWT")) {
          lwts.add(topic);
          log.info("[{}]: DISCOVERY: LWT from an unknown device {}", entityID, topic);
          for (String pattern : entity.getPatterns()) {
            Matcher matcher = Pattern.compile(pattern.replace("%topic%", "(?<topic>.*)")
                                                .replace("%prefix%", "(?<prefix>.*?)") + ".*$").matcher(topic);
            if (matcher.matches()) {
              String possible_topic = matcher.group("topic");
              if (!possible_topic.equals("tele") && !possible_topic.equals("stat")) {
                String possible_topic_cmnd = (pattern.replace("%prefix%", "cmnd").replace("%topic%", possible_topic) + "FullTopic");
                log.info("[{}]: DISCOVERY: Asking an unknown device for FullTopic at {}", entityID, possible_topic_cmnd);
                mqttEntityService.publish(possible_topic_cmnd);
              }
            }
          }
        } else if (topic.endsWith("RESULT") || topic.endsWith("FULLTOPIC")) {
          if (!payload.has("FullTopic")) {
            return;
          }
          String full_topic = payload.get("FullTopic").asText();
          ParsedTopic parsed = parseTopic(full_topic, topic);
          if (parsed == null) {
            return;
          }
          log.info("[{}]: DISCOVERY: topic {} is matched by fulltopic {}", entityID, topic, full_topic);
          MatchDeviceData deviceData = findDevice(parsed.topic);
          if (deviceData != null) {
            if (!deviceData.entity.getFullTopic().equals(full_topic)) {
              context.db().save(deviceData.entity.setFullTopic(full_topic));
            }
            deviceData.entity.getService().put("FullTopic", full_topic);
          } else {
            log.info("[{}]: DISCOVERY: Discovered topic={} with fulltopic={}", entityID, parsed.topic, full_topic);
            TasmotaDeviceEntity device = new TasmotaDeviceEntity();
            device.setIeeeAddress(parsed.topic);
            device.setFullTopic(full_topic);
            existedDevices.put(parsed.topic, context.db().save(device));
            log.info("[{}]: DISCOVERY: Sending initial query to topic {}", entityID, parsed.topic);
            initialQuery(device);
            String tele_topic = tele_topic(device, "LWT");
            lwts.remove(tele_topic);
            device.getService().put("LWT", "Online");
          }
        }
      });
    initialize();
  }

  private @Nullable MatchDeviceData findDevice(String topic) {
    for (TasmotaDeviceEntity entity : existedDevices.values()) {
      MatchDeviceData data = matchDevice(entity, topic);
      if (data != null) {
        return data;
      }
    }
    return null;
  }

  private MatchDeviceData matchDevice(TasmotaDeviceEntity entity, String topic) {
    if (entity.getIeeeAddress().equals(topic)) {
      return new MatchDeviceData(entity, "", "");
    }
    ParsedTopic parsedTopic = parseTopic(entity.getFullTopic(), topic);
    if (parsedTopic != null && parsedTopic.topic.equals(entity.getIeeeAddress())) {
      return new MatchDeviceData(entity, parsedTopic.reply(), parsedTopic.prefix());
    }
    return null;
  }

  public record MatchDeviceData(TasmotaDeviceEntity entity, String reply, String prefix) {
  }

  public record ParsedTopic(String prefix, @NotNull String topic, String reply) {

  }
}

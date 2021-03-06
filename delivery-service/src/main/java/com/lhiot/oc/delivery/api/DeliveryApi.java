package com.lhiot.oc.delivery.api;

import com.leon.microx.id.Generator;
import com.leon.microx.predefine.Day;
import com.leon.microx.util.DateTime;
import com.leon.microx.util.Maps;
import com.leon.microx.util.StringUtils;
import com.leon.microx.web.result.Id;
import com.leon.microx.web.result.Tips;
import com.lhiot.oc.delivery.client.AdaptableClient;
import com.lhiot.oc.delivery.entity.DeliverNote;
import com.lhiot.oc.delivery.feign.Store;
import com.lhiot.oc.delivery.model.*;
import com.lhiot.oc.delivery.service.DeliveryService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * @author Leon (234239150@qq.com) created in 9:09 18.11.9
 */
@Slf4j
@RestController
@Api("配送服务")
public class DeliveryApi {

    private static final LocalTime BEGIN_DELIVER_OF_DAY = LocalTime.parse("08:30:00");
    private static final LocalTime END_DELIVER_OF_DAY = LocalTime.parse("21:30:01");

    private static final DateTimeFormatter MM_DD = DateTimeFormatter.ofPattern("MM-dd");
    private static final DateTimeFormatter FULL = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss");
    private static final String NOT_FIND_DELIVERY_WAY_MESSAGE = "不支持的配送方式！";
    private static final String NOT_FIND_DELIVERY_ORDER_MESSAGE = "未找到配送单！";
    private final DeliveryService deliveryService;
    private final Generator<Long> generator;

    public DeliveryApi(DeliveryService deliveryService, Generator<Long> generator) {
        this.generator = generator;
        this.deliveryService = deliveryService;
    }


    @GetMapping("/delivery/times")
    @ApiOperation(value = "获取订单配送时间列表")
    public ResponseEntity deliverTimes(@RequestParam(value = "date", required = false) Day day) {
        LocalDateTime today = LocalDateTime.now();
        LocalDateTime tomorrow = today.toLocalDate().plusDays(1).atTime(BEGIN_DELIVER_OF_DAY);
        if (Objects.nonNull(day)) {
            switch (day) {
                case TODAY:
                    return ResponseEntity.ok(Maps.of("value", ofTimes(today), "date", today.format(MM_DD)));
                case TOMORROW:
                    return ResponseEntity.ok(Maps.of("value", ofTimes(tomorrow), "date", tomorrow.format(MM_DD)));
                default:
                    break; // ignore unsupported date.
            }
        }
        return ResponseEntity.ok(Maps.of(
                "today", Maps.of("value", ofTimes(today), "date", today.format(MM_DD)),
                "tomorrow", Maps.of("value", ofTimes(tomorrow), "date", tomorrow.format(MM_DD))
        ));
    }

    private static List<DeliverTime> ofTimes(LocalDateTime begin) {
        List<DeliverTime> times = new ArrayList<>();

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime latest = begin.toLocalDate().atTime(END_DELIVER_OF_DAY);
        LocalDateTime current = begin.withMinute(30);

        while (latest.compareTo(current) >= 0) {
            LocalDateTime next = current.plusHours(1);
            String display = current.getDayOfYear() == now.getDayOfYear() && current.getHour() == now.getHour()
                    ? "立即配送"
                    : StringUtils.format("{}-{}", current.format(FULL), next.format(FULL));
            times.add(DeliverTime.of(display, DateTime.convert(Objects.equals("立即配送", display) ? begin : current), DateTime.convert(next)));
            current = next;
        }
        return times;
    }

    @ApiOperation(value = "发送配送单")
    @PostMapping("/{deliverType}/delivery-notes")
    public ResponseEntity create(@PathVariable("deliverType") DeliverType type, @RequestParam("coordinate") CoordinateSystem coordinate, @RequestBody DeliverOrderParam deliverOrderParam) {
        Optional<Store> optional = deliveryService.store(deliverOrderParam.getStoreCode(), deliverOrderParam.getApplyType());
        if (!optional.isPresent()) {
            return ResponseEntity.badRequest().body("查询门店信息失败！");
        }
        long deliverNoteId = generator.get();
        AdaptableClient adapter = deliveryService.adapt(type);
        if (Objects.isNull(adapter)) {
            return ResponseEntity.badRequest().body(NOT_FIND_DELIVERY_WAY_MESSAGE);
        }
        Tips tips = adapter.send(coordinate, optional.get(), deliverOrderParam, deliverNoteId);
        if (tips.err()) {
            return ResponseEntity.badRequest().body(tips.getMessage());
        }
        DeliverNote deliverNote = (DeliverNote) tips.getData();
        deliverNote.setId(deliverNoteId);
        deliveryService.saveDeliverNote(deliverNote);
        return ResponseEntity.created(URI.create("/" + type.name() + "/delivery-notes/" + deliverNoteId)).body(Id.of(deliverNoteId));
    }

    @PutMapping("/delivery-notes/{code}")
    @ApiOperation(value = "更新配送单", response = String.class)
    public ResponseEntity update(@PathVariable("code") String code, @RequestBody DeliverUpdate deliverUpdate) {
        DeliverNote deliverNote = deliveryService.deliverNote(code);
        if (Objects.isNull(deliverNote)) {
            return ResponseEntity.badRequest().body(NOT_FIND_DELIVERY_ORDER_MESSAGE);
        }
        deliveryService.updateDeliverNote(deliverNote, deliverUpdate);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{deliverType}/delivery-notes/{hdOrderCode}")
    @ApiOperation(value = "取消配送", response = String.class)
    public ResponseEntity cancel(@PathVariable("deliverType") DeliverType type, @PathVariable("hdOrderCode") String hdOrderCode, CancelReason reason) {
        DeliverNote deliverNote = deliveryService.deliverNote(hdOrderCode);
        if (Objects.isNull(deliverNote)) {
            return ResponseEntity.badRequest().body(NOT_FIND_DELIVERY_ORDER_MESSAGE);
        }
        AdaptableClient adapter = deliveryService.adapt(type);
        if (Objects.isNull(adapter)) {
            return ResponseEntity.badRequest().body(NOT_FIND_DELIVERY_WAY_MESSAGE);
        }
        Tips tips = adapter.cancel(deliverNote, reason);
        if (tips.err()) {
            return ResponseEntity.badRequest().body(tips.getMessage());
        }
        // 配送状态流转记录
        DeliverStatus preStatus = deliverNote.getDeliverStatus();
        deliverNote.setFailureCause(reason.getReason());
        deliverNote.setCancelTime(new Date());
        deliverNote.setDeliverStatus(DeliverStatus.FAILURE);
        deliveryService.saveDeliverFlow(deliverNote, preStatus);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{deliverType}/delivery-notes/cancel/reasons")
    @ApiOperation(value = "配送取消原因列表", response = Object.class)
    public ResponseEntity cancelReasons(@PathVariable("deliverType") DeliverType type) {
        AdaptableClient adapter = deliveryService.adapt(type);
        if (Objects.isNull(adapter)) {
            return ResponseEntity.badRequest().body(NOT_FIND_DELIVERY_WAY_MESSAGE);
        }
        Tips tips = adapter.cancelReasons();
        if (tips.err()) {
            return ResponseEntity.badRequest().body(tips.getMessage());
        }
        return ResponseEntity.ok(tips.getData());
    }

    @GetMapping("/{deliverType}/delivery-notes/{code}")
    @ApiOperation(value = "配送单详细信息", response = String.class)
    public ResponseEntity detail(@PathVariable("deliverType") DeliverType type, @PathVariable("code") String code) {
        DeliverNote deliverNote = deliveryService.deliverNote(code);
        if (Objects.isNull(deliverNote)) {
            return ResponseEntity.badRequest().body(NOT_FIND_DELIVERY_ORDER_MESSAGE);
        }
        AdaptableClient adapter = deliveryService.adapt(type);
        if (Objects.isNull(adapter)) {
            return ResponseEntity.badRequest().body(NOT_FIND_DELIVERY_WAY_MESSAGE);
        }
        Tips tips = adapter.deliverNoteDetail(deliverNote);
        if (tips.err()) {
            return ResponseEntity.badRequest().body(tips.getMessage());
        }
        return ResponseEntity.ok(tips.getData());
    }

    @GetMapping("/delivery-notes/{code}")
    @ApiOperation(value = "查询本地配送单详情", response = DeliverNote.class)
    public ResponseEntity localDetail(@PathVariable("code") String code) {
        DeliverNote deliverNote = deliveryService.deliverNote(code);
        if (Objects.isNull(deliverNote)) {
            return ResponseEntity.badRequest().body("该配送单不存在");
        }
        return ResponseEntity.ok().body(deliverNote);
    }

    @ApiOperation(value = "配送单回调验签")
    @PostMapping("/{deliverType}/back-signature")
    public ResponseEntity backSignature(@PathVariable("deliverType") DeliverType type, @RequestBody Map<String, String> params) {
        AdaptableClient adapter = deliveryService.adapt(type);
        if (Objects.isNull(adapter)) {
            return ResponseEntity.badRequest().body(NOT_FIND_DELIVERY_WAY_MESSAGE);
        }
        Tips tips = adapter.backSignature(params);
        if (tips.err()) {
            return ResponseEntity.badRequest().body(tips.getMessage());
        }
        return ResponseEntity.ok(tips);
    }
}
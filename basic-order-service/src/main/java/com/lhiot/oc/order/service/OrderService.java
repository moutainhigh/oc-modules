package com.lhiot.oc.order.service;

import com.leon.microx.util.BeanUtils;
import com.leon.microx.util.Maps;
import com.leon.microx.util.SnowflakeId;
import com.leon.microx.util.StringUtils;
import com.leon.microx.web.result.Tips;
import com.lhiot.oc.order.event.OrderFlowEvent;
import com.lhiot.oc.order.mapper.*;
import com.lhiot.oc.order.model.*;
import com.lhiot.oc.order.model.type.OrderStatus;
import com.lhiot.oc.order.model.type.ReceivingWay;
import com.lhiot.oc.order.model.type.RefundStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * @author zhangfeng created in 2018/9/19 9:19
 **/
@Service
@Slf4j
@Transactional
public class OrderService {

    private BaseOrderMapper baseOrderMapper;
    private OrderProductMapper orderProductMapper;
    private OrderStoreMapper orderStoreMapper;
    private OrderFlowMapper orderFlowMapper;
    private SnowflakeId snowflakeId;
    private OrderRefundMapper orderRefundMapper;
    private ApplicationEventPublisher publisher;

    public OrderService(BaseOrderMapper baseOrderMapper, OrderProductMapper orderProductMapper, OrderStoreMapper orderStoreMapper, OrderFlowMapper orderFlowMapper, SnowflakeId snowflakeId, OrderRefundMapper orderRefundMapper, ApplicationEventPublisher publisher) {
        this.baseOrderMapper = baseOrderMapper;
        this.orderProductMapper = orderProductMapper;
        this.orderStoreMapper = orderStoreMapper;
        this.orderFlowMapper = orderFlowMapper;
        this.snowflakeId = snowflakeId;
        this.orderRefundMapper = orderRefundMapper;
        this.publisher = publisher;
    }

    /**
     * 添加订单信息
     *
     * @param param CreateOrderParam
     * @return OrderDetailResult
     */
    public OrderDetailResult createOrder(CreateOrderParam param) {
        BaseOrder baseOrder = param.toOrderObject();
        String orderCode = snowflakeId.stringId();
        baseOrder.setCode(orderCode);
        baseOrder.setHdOrderCode(orderCode);
        baseOrderMapper.insert(baseOrder);

        List<OrderProduct> productList = param.getOrderProducts();
        for (OrderProduct orderProduct : productList) {
            orderProduct.setOrderId(baseOrder.getId());
        }
        orderProductMapper.batchInsert(param.getOrderProducts());

        OrderStore orderStore = param.getOrderStore();
        orderStore.setHdOrderCode(orderCode);
        orderStore.setOrderId(baseOrder.getId());
        orderStoreMapper.insert(orderStore);

        OrderDetailResult orderDetail = new OrderDetailResult();
        BeanUtils.of(orderDetail).populate(baseOrder);
        orderDetail.setOrderProductList(productList);
        orderDetail.setOrderStore(orderStore);
        return orderDetail;
    }


    /**
     * 验证创建订单数据 可以是套餐 也可以是商品
     *
     * @param param 创建订单参数
     * @return Tips
     */
    public Tips validationParam(CreateOrderParam param) {
        //应付金额为空或者小于零
        if (param.getAmountPayable() < 0) {
            return Tips.warn("应付金额为空或者小于零");
        }
        if (param.getCouponAmount() > param.getTotalAmount()) {
            return Tips.warn("优惠金额不能大于订单总金额");
        }
        //不算优惠商品应付金额
        int productPayable = param.getAmountPayable() + param.getCouponAmount();
        //商品为空
        List<OrderProduct> orderProducts = param.getOrderProducts();
        if (CollectionUtils.isEmpty(orderProducts)) {
            return Tips.warn("商品为空");
        }
        int productAmount = 0;
        //校验商品数量
        for (OrderProduct orderProduct : orderProducts) {
            //判断传入购买份数
            if (Objects.isNull(orderProduct.getProductQty()) || orderProduct.getProductQty() <= 0) {
                return Tips.warn("商品购买数量为0");
            }
            productAmount += orderProduct.getTotalPrice();
        }
        if (!Objects.equals(productPayable, productAmount) || !Objects.equals(param.getTotalAmount(), productAmount)) {
            return Tips.warn("订单传入的金额有误");
        }
        //送货上门的订单，地址不能为空
        if (ReceivingWay.TO_THE_HOME.equals(param.getReceivingWay()) && Objects.isNull(param.getAddress())) {
            return Tips.warn("送货上门，地址为空");
        }
        return Tips.info("校验成功");
    }

    /**
     * 依据订单编码退货
     *
     * @param baseOrder   BaseOrder
     * @param orderRefund OrderRefund
     * @return int
     */
    public int refundOrderByCode(BaseOrder baseOrder, OrderRefund orderRefund) {

        int result = this.baseOrderMapper.updateOrderStatusByCode(baseOrder);
        if (result > 0) {
            //记录退货表
            orderRefundMapper.insert(orderRefund);
            //写退款订单商品标识
            List<String> orderProductIds = Arrays.asList(StringUtils.tokenizeToStringArray(orderRefund.getOrderProductIds(), ","));
            orderProductMapper.updateOrderProductByIds(Maps.of("orderId", baseOrder.getId(),
                    "refundStatus", RefundStatus.REFUND,
                    "orderProductIds", orderProductIds));
        }
        return result;
    }

    /**
     * 门店调货
     *
     * @param targetStore   Store
     * @param operationUser String
     * @param orderId       Long
     * @return int
     */
    public int changeStore(Store targetStore, String operationUser, Long orderId,String hdOrderCode) {

        BaseOrder baseOrder = new BaseOrder();
        baseOrder.setId(orderId);
        baseOrder.setHdOrderCode(hdOrderCode);
        int result = baseOrderMapper.updateHdOrderCodeById(baseOrder);

        if (result > 0) {
            //设置调货订单门店信息
            OrderStore orderStore = new OrderStore();
            orderStore.setHdOrderCode(hdOrderCode);
            orderStore.setOrderId(orderId);
            orderStore.setStoreId(targetStore.getId());
            orderStore.setStoreName(targetStore.getName());
            orderStore.setStoreCode(targetStore.getCode());
            orderStore.setOperationUser(operationUser);
            orderStore.setCreateAt(Date.from(Instant.now()));
            orderStoreMapper.insert(orderStore);
        }
        return result;
    }

    public Tips updateStatus(OrderDetailResult orderDetailResult){
        BaseOrder baseOrder = new BaseOrder();
        baseOrder.setCode(orderDetailResult.getCode());
        baseOrder.setStatus(OrderStatus.SEND_OUT);
        int result = baseOrderMapper.updateOrderStatusByCode(baseOrder);
        if (result > 0) {
            publisher.publishEvent(new OrderFlowEvent(orderDetailResult.getStatus(), OrderStatus.SEND_OUT, orderDetailResult.getId()));
            return Tips.empty();
        }
        return Tips.warn("修改状态失败");
    }


    @Nullable
    public OrderDetailResult findByCode(String code, boolean needProductList, boolean needOrderFlowList) {
        OrderDetailResult searchBaseOrderInfo = this.baseOrderMapper.selectByCode(code);
        if (Objects.isNull(searchBaseOrderInfo)) {
            return null;
        }
       this.addAttachments(searchBaseOrderInfo,needProductList,needOrderFlowList);
        return searchBaseOrderInfo;
    }

    @Nullable
    public OrderDetailResult findByCode(String code) {
        return this.findByCode(code, false, false);
    }

    @Nullable
    public OrderDetailResult findById(Long id, boolean needProductList, boolean needOrderFlowList) {
        OrderDetailResult searchBaseOrderInfo = this.baseOrderMapper.selectById(id);
        if (Objects.isNull(searchBaseOrderInfo)) {
            return null;
        }
        this.addAttachments(searchBaseOrderInfo,needProductList,needOrderFlowList);
        return searchBaseOrderInfo;
    }

    @Nullable
    public OrderDetailResult findById(Long id) {
        return this.findById(id, false, false);
    }

    private void addAttachments(OrderDetailResult searchBaseOrderInfo,boolean needProductList, boolean needOrderFlowList){
        searchBaseOrderInfo.setOrderStore(orderStoreMapper.findByHdOrderCode(searchBaseOrderInfo.getHdOrderCode()));
        if (needProductList) {
            searchBaseOrderInfo.setOrderProductList(orderProductMapper.selectOrderProductsByOrderId(searchBaseOrderInfo.getId()));
        }
        if (needOrderFlowList) {
            searchBaseOrderInfo.setOrderFlowList(orderFlowMapper.selectFlowByOrderId(searchBaseOrderInfo.getId()));
        }
    }
}

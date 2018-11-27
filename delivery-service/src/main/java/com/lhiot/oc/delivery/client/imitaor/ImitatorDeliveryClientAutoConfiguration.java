package com.lhiot.oc.delivery.client.imitaor;

import com.leon.microx.util.Jackson;
import com.lhiot.oc.delivery.client.DadaAdapter;
import com.lhiot.oc.delivery.client.DeliveryClientProperties;
import com.lhiot.oc.delivery.client.FengNiaoAdapter;
import com.lhiot.oc.delivery.client.MeiTuanAdapter;
import com.lhiot.oc.delivery.client.dada.DadaClient;
import com.lhiot.oc.delivery.client.fengniao.FengNiaoClient;
import com.lhiot.oc.delivery.client.meituan.MeiTuanClient;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Leon (234239150@qq.com) created in 20:19 18.11.13
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(DeliveryClientProperties.class)
public class ImitatorDeliveryClientAutoConfiguration {

    @Bean
    public ImitatorDadaAdapter dadaImitatorAdapter(DeliveryClientProperties properties) {
        log.info("\t===>>\t[DaDa Delivery Client] initialized");
        DadaClient dadaClient = new DadaClient(properties.getDada(), Jackson::json);
        log.info("\t===>>\t[DaDa Delivery ImitatorAdapter]  initializing......");
        return new ImitatorDadaAdapter(dadaClient);
    }
}

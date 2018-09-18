package com.lhiot.oc.basic.service;

import com.lhiot.oc.basic.domain.DeliverOrderProduct;
import com.lhiot.oc.basic.domain.common.PagerResultObject;
import com.lhiot.oc.basic.mapper.DeliverOrderProductMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;

/**
* Description:配送订单商品列服务类
* @author yijun
* @date 2018/09/18
*/
@Service
@Transactional
public class DeliverOrderProductService {

    private final DeliverOrderProductMapper deliverOrderProductMapper;

    @Autowired
    public DeliverOrderProductService(DeliverOrderProductMapper deliverOrderProductMapper) {
        this.deliverOrderProductMapper = deliverOrderProductMapper;
    }


    /** 
    * Description:根据id修改配送订单商品列
    *  
    * @param deliverOrderProduct
    * @return
    * @author yijun
    * @date 2018/09/18 09:41:12
    */ 
    public int updateById(DeliverOrderProduct deliverOrderProduct){
        return this.deliverOrderProductMapper.updateById(deliverOrderProduct);
    }

    /** 
    * Description:根据ids删除配送订单商品列
    *  
    * @param ids
    * @return
    * @author yijun
    * @date 2018/09/18 09:41:12
    */ 
    public int deleteByIds(String ids){
        return this.deliverOrderProductMapper.deleteByIds(Arrays.asList(ids.split(",")));
    }
    
    /** 
    * Description:根据id查找配送订单商品列
    *  
    * @param id
    * @return
    * @author yijun
    * @date 2018/09/18 09:41:12
    */ 
    public DeliverOrderProduct selectById(Long id){
        return this.deliverOrderProductMapper.selectById(id);
    }

    /** 
    * Description: 查询配送订单商品列总记录数
    *  
    * @param deliverOrderProduct
    * @return
    * @author yijun
    * @date 2018/09/18 09:41:12
    */  
    public long count(DeliverOrderProduct deliverOrderProduct){
        return this.deliverOrderProductMapper.pageDeliverOrderProductCounts(deliverOrderProduct);
    }
    
    /** 
    * Description: 查询配送订单商品列分页列表
    *  
    * @param deliverOrderProduct
    * @return
    * @author yijun
    * @date 2018/09/18 09:41:12
    */  
    public PagerResultObject<DeliverOrderProduct> pageList(DeliverOrderProduct deliverOrderProduct) {
       long total = 0;
       if (deliverOrderProduct.getRows() != null && deliverOrderProduct.getRows() > 0) {
           total = this.count(deliverOrderProduct);
       }
       return PagerResultObject.of(deliverOrderProduct, total,
              this.deliverOrderProductMapper.pageDeliverOrderProducts(deliverOrderProduct));
    }
}


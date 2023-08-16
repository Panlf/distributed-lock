package com.plf.lock.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.plf.lock.bean.Stock;

/**
 * @author panlf
 * @date 2023/8/1
 */
public interface StockService extends IService<Stock> {
    void deduct();
}

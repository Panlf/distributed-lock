package com.plf.lock.controller;

import com.plf.lock.bean.Stock;
import com.plf.lock.service.StockService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

/**
 * @author panlf
 * @date 2023/8/1
 */
@RestController
@RequestMapping("stock")
public class StockController {

    @Resource
    private StockService stockService;

    @GetMapping("list")
    public List<Stock> list(){
        return stockService.list();
    }

    @GetMapping("deduct")
    public void deduct(){
        stockService.deduct();
    }
}


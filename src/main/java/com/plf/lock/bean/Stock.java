package com.plf.lock.bean;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * @author panlf
 * @date 2023/7/31
 */
@Data
@TableName("stock")
public class Stock {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String productName;

    private Long count;

    private Long version;
}

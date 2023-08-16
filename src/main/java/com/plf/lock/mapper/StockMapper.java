package com.plf.lock.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.plf.lock.bean.Stock;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * @author panlf
 * @date 2023/8/1
 */
@Mapper
public interface StockMapper extends BaseMapper<Stock> {

    @Update("update stock set count = count - #{count} where id = #{id} and count >= #{count}")
    void updateStock(@Param("id") Integer id,@Param("count") Integer count);

    @Select("select * from stock where id = #{id} for update")
    List<Stock> queryStock(Integer id);
}

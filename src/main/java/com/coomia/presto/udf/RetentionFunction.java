/**
 * Copyright (c) 2015-2020 Coomia Network Technology Co., Ltd. All Rights Reserved.
 *
 * <p>
 * This software is licensed not sold. Use or reproduction of this software by any unauthorized individual or entity is
 * strictly prohibited. This software is the confidential and proprietary information of Coomia Network Technology Co.,
 * Ltd. Disclosure of such confidential information and shall use it only in accordance with the terms of the license
 * agreement you entered into with Coomia Network Technology Co., Ltd.
 *
 * <p>
 * Coomia Network Technology Co., Ltd. MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF THE SOFTWARE,
 * EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE, OR NON-INFRINGEMENT. Coomia Network Technology Co., Ltd. SHALL NOT BE LIABLE FOR ANY DAMAGES
 * SUFFERED BY LICENSEE AS A RESULT OF USING, MODIFYING OR DISTRIBUTING THIS SOFTWARE OR ANY DERIVATIVES THEREOF.
 */

package com.coomia.presto.udf;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import io.airlift.slice.Slice;
import io.airlift.slice.Slices;
import io.prestosql.spi.block.BlockBuilder;
import io.prestosql.spi.function.AggregationFunction;
import io.prestosql.spi.function.CombineFunction;
import io.prestosql.spi.function.Description;
import io.prestosql.spi.function.InputFunction;
import io.prestosql.spi.function.OutputFunction;
import io.prestosql.spi.function.SqlType;
import io.prestosql.spi.type.DoubleType;
import io.prestosql.spi.type.StandardTypes;

/**
 * 这个函数
 * 
 * @author spancer
 * @date 2020/07/05
 */
@AggregationFunction("retention")
@Description("retention function")
public class RetentionFunction {

    // 时间需要格式化为这个格式
    private static final String DATE_FORMAT = "yyyy-MM-dd";

    @InputFunction
    public static void input(SliceState state, // 每个分组的状态
        @SqlType(StandardTypes.BIGINT) long eventTime, // 事件时间，原始值，单位：ms
        @SqlType(StandardTypes.INTEGER) long startDate, // 开始时间
        @SqlType(StandardTypes.INTEGER) long timeWin) { // 时间窗口，如7天留存
        Slice slice = state.getSlice();
        // slice定长。长度为timeWin+1个int byte
        if (null == slice)
            slice = Slices.allocate(((int)timeWin + 1)*Integer.BYTES);
        // 将事件时间格式化为yyyy-MM-dd格式，为了与开始时间，结束时间算间隔
        String xwhen = new SimpleDateFormat(DATE_FORMAT).format(new Date(eventTime));
        String xstart = intDateToString((int)startDate);
        int days = days(xstart, xwhen);
        if (days < 0 || days>timeWin)
            return;
        slice.setInt(days*Integer.BYTES, 1);
        state.setSlice(slice);
    }

    @CombineFunction
    public static void combine(SliceState state, SliceState otherState) {
        // 获取计算中间状态
        Slice slice = state.getSlice();
        Slice otherSlice = otherState.getSlice();
        // 更新状态, 并返回结果
        if (null == slice) {
            state.setSlice(otherSlice);
        } else {
            // 存的是byte,直接index += Byte.BYTES
            for (int index = 0; index < slice.length(); index += Integer.BYTES) {
                // 各节点的中间结果按位做或运算，有留存的就是1，没有的就是0
                slice.setInt(index, slice.getByte(index) | otherSlice.getByte(index));
            }
            state.setSlice(slice);
        }
    }

    /**
     * 返回int array.
     * 
     * @param state
     * @param out
     */
    @OutputFunction("array(double)")
    public static void output(SliceState state, BlockBuilder out) {
        // 获取最终的聚合状态
        Slice slice = state.getSlice();
        if (null == slice) {
            out.appendNull();
            return;
        }
        // 返回结果
        BlockBuilder blockBuilder = out.beginBlockEntry();
        for (int index = 0; index < slice.length(); index += Integer.BYTES) {
            DoubleType.DOUBLE.writeDouble(blockBuilder, slice.getInt(index));
        }
        out.closeEntry();
    }

    /**
     * Date should be in format yyyy-MM-dd.
     * 
     * @param date1
     * @param date2
     * @return
     */
    public static int days(String date1, String date2) {
        LocalDate dt1 = LocalDate.parse(date1);
        LocalDate dt2 = LocalDate.parse(date2);
        long diffDays = ChronoUnit.DAYS.between(dt1, dt2);
        return (int)diffDays;
    }

    public static String intDateToString(Integer date) {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd");
        formatter.setLenient(false);
        Date newDate = null;
        try {
            newDate = formatter.parse(date.toString());
        } catch (ParseException e) {
            e.printStackTrace();
        }
        formatter = new SimpleDateFormat(DATE_FORMAT);
        return formatter.format(newDate);
    }
}

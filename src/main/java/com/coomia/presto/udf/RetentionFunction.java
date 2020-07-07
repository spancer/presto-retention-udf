/*******************************************************************************
 *
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
 *******************************************************************************/

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
 * retention(event_time, query_date, time_window) 返回time_window大小(如7天）的[0,0,0,0,0,0,0] slice，每位记录该用户在当天是否 满足事件发生（发登录）
 * 
 * @author spancer
 * @date 2020/07/04
 */
@AggregationFunction("retention")
@Description("retention function by 雷鹏")
public class RetentionFunction {

    // 时间需要格式化为这个格式
    private static final String DATE_FORMAT = "yyyy-MM-dd";

    @InputFunction
    public static void input(SliceState state, // 每个分组的状态
        @SqlType(StandardTypes.BIGINT) long eventTime, // 事件时间，原始值，单位：ms
        @SqlType(StandardTypes.INTEGER) long startDate, // 开始时间(yyMMdd)
        @SqlType(StandardTypes.INTEGER) long timeWin) { // 时间窗口，如7天留存 
        Slice slice = state.getSlice();
        // slice定长，用一个INT数字(32bit)来表示timeWin各时间是否有值。
        if (null == slice)
            slice = Slices.allocate(Integer.BYTES);
        // 将事件时间格式化为yyyy-MM-dd格式，为了与开始时间，结束时间算间隔
        String xwhen = new SimpleDateFormat(DATE_FORMAT).format(new Date(eventTime));
        String xstart = intDateToString((int)startDate);
        int days = days(xstart, xwhen); // 求两个时间的间隔
        if (days < 0 || days > timeWin)
            return;
        byte var = slice.getByte(0);
        slice.setInt(0, (byte)(var | (0x1 << days)));
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
            slice.setInt(0, slice.getByte(0) | otherSlice.getByte(0));
            state.setSlice(slice);
        }
    }

    /**
     * 返回double array.
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
        int result = slice.getInt(0);
        String binary = Integer.toBinaryString(result);
        for (int i = 0; i < binary.length(); i++) {
            DoubleType.DOUBLE.writeDouble(blockBuilder, binary.charAt(i));
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

package com.filez.demo.common.utils;

import com.alibaba.fastjson.serializer.JSONSerializer;
import com.alibaba.fastjson.serializer.ObjectSerializer;
import com.alibaba.fastjson.serializer.SerializeWriter;

import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

/**
 * Date转换成UTC时间格式工具类
 */
public class DateToUtcZUtil implements ObjectSerializer {

    private static final SimpleDateFormat sdf;

    static {
        sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    @Override
    public void write(JSONSerializer serializer, Object object, Object fieldName, Type fieldType, int features) {
        SerializeWriter out = serializer.getWriter();
        if (object == null) {
            out.writeNull();
            return;
        }

        out.writeString(sdf.format((Date) object));
    }
}

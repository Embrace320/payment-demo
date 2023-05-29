package com.anthony.valueobject;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Anthony_CMH
 * @create 2023-05-16 10:42
 * Usage 规范前后端交互数据格式
 */
@Data
@Accessors(chain=true)
@NoArgsConstructor
@AllArgsConstructor
public class R {
    private Integer code; // 响应码
    private String message; // 响应信息
    private Map<String, Object> data = new HashMap<>(); //响应数据

    public static R ok(){
        R r = new R();
        r.setCode(0);
        r.setMessage("成功");
        return r;
    }

    public static R error(){
        R r = new R();
        r.setCode(-1);
        r.setMessage("失败");
        return r;
    }

    public R data(String key, Object value){
        this.data.put(key, value);
        return this;
    }
}

package org.example.hragent.vo;
import lombok.Data;

/**
 * 统一接口返回结果
 * @param <T> 返回数据类型
 */
@Data
public class R<T> {

    /**
     * 响应状态码
     */
    private Integer code;

    /**
     * 响应提示信息
     */
    private String msg;

    /**
     * 返回业务数据
     */
    private T data;

    /**
     * 请求成功，无返回数据
     * @return 成功响应对象
     */
    public static <T> R<T> ok() {
        R<T> r = new R<>();
        r.setCode(200);
        r.setMsg("success");
        return r;
    }

    /**
     * 请求成功，携带返回数据
     * @param data 业务返回数据
     * @return 成功响应对象
     */
    public static <T> R<T> ok(T data) {
        R<T> r = new R<>();
        r.setCode(200);
        r.setMsg("success");
        r.setData(data);
        return r;
    }

    /**
     * 请求失败
     * @param code 错误码
     * @param msg 错误提示信息
     * @return 失败响应对象
     */
    public static <T> R<T> fail(Integer code, String msg) {
        R<T> r = new R<>();
        r.setCode(code);
        r.setMsg(msg);
        return r;
    }
}
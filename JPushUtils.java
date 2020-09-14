/**
 * @Author: lvzheng
 * @Date: 2020/9/6 12:39 下午
 */
package com.whhim.szh.common.until;

import cn.jiguang.common.ClientConfig;
import cn.jiguang.common.resp.APIConnectionException;
import cn.jiguang.common.resp.APIRequestException;
import cn.jpush.api.JPushClient;
import cn.jpush.api.push.PushResult;
import cn.jpush.api.push.model.Options;
import cn.jpush.api.push.model.Platform;
import cn.jpush.api.push.model.PushPayload;
import cn.jpush.api.push.model.audience.Audience;
import cn.jpush.api.push.model.audience.AudienceTarget;
import cn.jpush.api.push.model.notification.AndroidNotification;
import cn.jpush.api.push.model.notification.IosAlert;
import cn.jpush.api.push.model.notification.IosNotification;
import cn.jpush.api.push.model.notification.Notification;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.whhim.szh.common.exception.JPushException;
import com.whhim.szh.common.model.jpush.PushParam;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;

import java.util.List;

/**
 * 极光推送工具
 */
@Slf4j
public class JPushUtils {

    /**
     *  成功或没有目标返回true，因为可能用户没有在任何一台设备登录
     *  极光API调用频率超出限制，或者调用极光http API连接错误，需要过一段时间重试，返回false
     *  其他错误抛出JPushException
     * @param pushParam 传入参数
     * @param masterSecret 极光配置masterSecret
     * @param appKey 极光配置appKey
     * @throws JPushException 其他错误抛出JPushException
     * @return false的情况需要重试
     */
    public static boolean sendPush(
            PushParam pushParam,
            String masterSecret,
            String appKey) throws JPushException {
        ClientConfig config = ClientConfig.getInstance();
        config.setGlobalPushSetting(false, 24 * 60 * 60 * 2);
        JPushClient jpushClient =
                new JPushClient(masterSecret, appKey, null, config);

        PushPayload payload = buildPushPayload(pushParam);

        try {
            PushResult result = jpushClient.sendPush(payload);
            if (result.isResultOK()) {
                return true;
            } else {
                if (result.error.getCode() == 2002) {
                    log.warn("极光API调用频率超出限制");
                    return false;
                } else if (result.error.getCode() == 1011) {
                    log.info("没有设备满足推送目标，可能是因为没有登录");
                    return true;
                } else {
                    String errorMessage = "极光API出错, statusCode=" + result.getResponseCode()
                            + ", code=" + result.error.getCode()
                            + ", msg=" + result.error.getMessage();
                    log.error(errorMessage);
                    throw new JPushException(errorMessage);
                }
            }
        } catch (APIConnectionException e) {
            String errorMessage = "调用极光http API连接错误，需要过一段时间重试";
            log.error(errorMessage, e);
            return false;
        } catch (APIRequestException e) {
            String errorMessage = "系统内部错误：调用极光推送http API时参数错误，需要修复请求参数";
            log.error(errorMessage, e);
            log.error("HTTP Status: " + e.getStatus());
            log.error("Error Code: " + e.getErrorCode());
            log.error("Error Message: " + e.getErrorMessage());
            throw new JPushException(errorMessage);
        }


    }



    private static PushPayload buildPushPayload(PushParam pushParam) {
        List<PushParam.Platform> platforms = pushParam.getPlatforms();
        List<String> aliases = pushParam.getAliases();
        JsonObject intent = null;
        if (pushParam.getIntent() != null) {
            intent = new JsonObject();
            intent.add("url", new JsonPrimitive(pushParam.getIntent().getUrl()));
        }

        PushPayload.Builder builder =  PushPayload.newBuilder();
        if(platforms.contains(PushParam.Platform.ANDROID)
                && platforms.contains(PushParam.Platform.IOS)) {
            builder.setPlatform(Platform.android_ios());
        } else if (platforms.contains(PushParam.Platform.ANDROID)) {
            builder.setPlatform(Platform.android());
        } else if (platforms.contains(PushParam.Platform.IOS)) {
            builder.setPlatform(Platform.ios());
        }
        builder.setAudience(Audience.alias(aliases));

        Notification.Builder notificationBuilder = Notification.newBuilder();
        if (platforms.contains(PushParam.Platform.ANDROID)) {
            AndroidNotification.Builder androidNotifiBuilder =
                    AndroidNotification.newBuilder()
                            .setTitle(pushParam.getTitle())
                            .setAlert(pushParam.getMessage())
                            .addExtras(pushParam.getExtras());
            if (pushParam.getIntent() != null) {
                androidNotifiBuilder.setIntent(intent);
            }
            notificationBuilder.addPlatformNotification(androidNotifiBuilder.build());
        }
        if (platforms.contains(PushParam.Platform.IOS)) {
            IosAlert alert = IosAlert.newBuilder()
                    .setTitleAndBody(pushParam.getTitle(), null, pushParam.getMessage())
                    .build();
            notificationBuilder.addPlatformNotification(IosNotification.newBuilder()
                    .setAlert(alert)
                    .setContentAvailable(true)
                    .setMutableContent(true)
                    .addExtras(pushParam.getExtras())
                    .autoBadge()
                    .build());
        }

        return builder.setNotification(notificationBuilder.build())
                .setOptions(Options.newBuilder()
                        .setApnsProduction(false)
                        .setTimeToLive(24 * 60 * 60 * 2)
                        .build())
                .build();
    }
}

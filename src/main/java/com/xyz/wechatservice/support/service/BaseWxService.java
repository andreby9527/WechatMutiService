package com.xyz.wechatservice.support.service;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.xyz.wechatservice.support.RedisClient;
import com.xyz.wechatservice.support.WXServiceHandler;
import com.xyz.wechatservice.support.config.WxConfig;
import com.xyz.wechatservice.support.handler.AbstractHandler;
import com.xyz.wechatservice.support.handler.KfSessionHandler;
import com.xyz.wechatservice.support.handler.LogHandler;
import com.xyz.wechatservice.support.handler.MenuHandler;
import com.xyz.wechatservice.support.handler.MsgHandler;
import com.xyz.wechatservice.support.handler.NullHandler;
import com.xyz.wechatservice.support.handler.StoreCheckNotifyHandler;
import com.xyz.wechatservice.support.handler.SubscribeHandler;
import com.xyz.wechatservice.support.handler.UnsubscribeHandler;

import me.chanjar.weixin.common.api.WxConsts;
import me.chanjar.weixin.mp.api.WxMpMessageRouter;
import me.chanjar.weixin.mp.api.impl.WxMpServiceImpl;
import me.chanjar.weixin.mp.bean.kefu.result.WxMpKfOnlineList;
import me.chanjar.weixin.mp.bean.message.WxMpXmlMessage;
import me.chanjar.weixin.mp.bean.message.WxMpXmlOutMessage;
import me.chanjar.weixin.mp.constant.WxMpEventConstants;

/**
 * 
 * @author Binary Wang
 *
 */
public abstract class BaseWxService extends WxMpServiceImpl implements WXServiceHandler {
	private final Logger logger = LoggerFactory.getLogger(this.getClass());

	@Autowired
	protected LogHandler logHandler;

	@Autowired
	protected NullHandler nullHandler;

	@Autowired
	protected KfSessionHandler kfSessionHandler;

	@Autowired
	protected StoreCheckNotifyHandler storeCheckNotifyHandler;

	private WxMpMessageRouter router;

	protected abstract WxConfig getServerConfig();

	public abstract void setWxConfig(WxConfig wxConfig);

	protected abstract MenuHandler getMenuHandler();

	protected abstract SubscribeHandler getSubscribeHandler();

	protected abstract UnsubscribeHandler getUnsubscribeHandler();

	protected abstract AbstractHandler getLocationHandler();

	protected abstract MsgHandler getMsgHandler();

	protected abstract AbstractHandler getScanHandler();

	protected RedisClient redisClient;

	public RedisClient getRedisClient() {
		return redisClient;
	}

	public void setRedisClient(RedisClient redisClient) {
		this.redisClient = redisClient;
	}

	@PostConstruct
	public void init() {
		this.refreshRouter();
	}

	private void refreshRouter() {

		final WxMpMessageRouter newRouter = new WxMpMessageRouter(this);

		// 记录所有事件的日志
		newRouter.rule().handler(this.logHandler).next();

		// 接收客服会话管理事件
		newRouter.rule().async(false).msgType(WxConsts.XML_MSG_EVENT)
				.event(WxMpEventConstants.CustomerService.KF_CREATE_SESSION).handler(this.kfSessionHandler).end();
		newRouter.rule().async(false).msgType(WxConsts.XML_MSG_EVENT)
				.event(WxMpEventConstants.CustomerService.KF_CLOSE_SESSION).handler(this.kfSessionHandler).end();
		newRouter.rule().async(false).msgType(WxConsts.XML_MSG_EVENT)
				.event(WxMpEventConstants.CustomerService.KF_SWITCH_SESSION).handler(this.kfSessionHandler).end();

		// 门店审核事件
		newRouter.rule().async(false).msgType(WxConsts.XML_MSG_EVENT).event(WxConsts.XML_MSG_EVENT)
				.handler(this.storeCheckNotifyHandler).end();

		// 自定义菜单事件
		newRouter.rule().async(false).msgType(WxConsts.XML_MSG_EVENT).event(WxConsts.BUTTON_CLICK)
				.handler(this.getMenuHandler()).end();

		// 点击菜单连接事件
		newRouter.rule().async(false).msgType(WxConsts.XML_MSG_EVENT).event(WxConsts.BUTTON_VIEW)
				.handler(this.nullHandler).end();

		// 关注事件
		newRouter.rule().async(false).msgType(WxConsts.XML_MSG_EVENT).event(WxConsts.EVT_SUBSCRIBE)
				.handler(this.getSubscribeHandler()).end();

		// 取消关注事件
		newRouter.rule().async(false).msgType(WxConsts.XML_MSG_EVENT).event(WxConsts.EVT_UNSUBSCRIBE)
				.handler(this.getUnsubscribeHandler()).end();

		// 上报地理位置事件
		newRouter.rule().async(false).msgType(WxConsts.XML_MSG_EVENT).event(WxConsts.EVT_LOCATION)
				.handler(this.getLocationHandler()).end();

		// 接收地理位置消息
		newRouter.rule().async(false).msgType(WxConsts.XML_MSG_LOCATION).handler(this.getLocationHandler()).end();

		// 扫码事件
		newRouter.rule().async(false).msgType(WxConsts.XML_MSG_EVENT).event(WxConsts.EVT_SCAN)
				.handler(this.getScanHandler()).end();

		// 默认
		newRouter.rule().async(false).handler(this.getMsgHandler()).end();

		this.router = newRouter;
	}

	public WxMpXmlOutMessage route(WxMpXmlMessage message) {
		try {
			return this.router.route(message);
		} catch (Exception e) {
			this.logger.error(e.getMessage(), e);
		}

		return null;
	}

	public boolean hasKefuOnline() {
		try {
			WxMpKfOnlineList kfOnlineList = this.getKefuService().kfOnlineList();
			return kfOnlineList != null && kfOnlineList.getKfOnlineList().size() > 0;
		} catch (Exception e) {
			this.logger.error("获取客服在线状态异常: " + e.getMessage(), e);
		}

		return false;
	}

}

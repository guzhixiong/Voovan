package org.hocate.http.server;

import org.hocate.http.message.Request;
import org.hocate.http.message.packet.Cookie;

public class HttpRequest extends Request {

	private HttpSession session;
	private String remoteAddres;
	private int remotePort;
	private String characterSet;
	
	protected HttpRequest(Request request){
		super(request);
		characterSet="UTF-8";
	}
	
	/**
	 * 根据 Cookie 名称取 Cookie
	 * @param name
	 * @return
	 */
	public Cookie getCookie(String name){
		for(Cookie cookie : this.cookies()){
			if(cookie.getName().equals(name)){
				return cookie;
			}
		}
		return null;
	}

	/**
	 * 获取 Session
	 * @return
	 */
	public HttpSession getSession() {
		return session;
	}

	/**
	 * 设置一个 Session
	 * @param session
	 */
	protected void setSession(HttpSession session) {
		this.session = session;
	}

	/**
	 * 获取对端连接的 IP
	 * @return
	 */
	public String getRemoteAddres() {
		return remoteAddres;
	}

	/**
	 * 设置对端连接的 IP
	 * @param remoteAddres
	 */
	protected void setRemoteAddres(String remoteAddres) {
		this.remoteAddres = remoteAddres;
	}

	/**
	 * 获取对端连接的端口
	 * @return
	 */
	public int getRemotePort() {
		return remotePort;
	}

	/**
	 * 设置对端连接的端口
	 * @param port
	 */
	protected void setRemotePort(int port) {
		this.remotePort = port;
	}

	/**
	 * 获取当前默认字符集
	 * @return
	 */
	public String getCharacterSet() {
		return characterSet;
	}

	/**
	 * 设置当前默认字符集
	 * @param charset
	 */
	public void setCharacterSet(String charset) {
		this.characterSet = charset;
	}
	
	/**
	 * 获取请求字符串
	 * @return
	 */
	public String getQueryString(){
		return getQueryString(characterSet);
	}
}
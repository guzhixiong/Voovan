package org.hocate.http;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Vector;

import org.hocate.http.message.HttpRequest;
import org.hocate.http.message.HttpResponse;
import org.hocate.http.message.packet.Cookie;
import org.hocate.http.message.packet.Part;
import org.hocate.log.Logger;
import org.hocate.tools.TObject;
import org.hocate.tools.TStream;
import org.hocate.tools.TString;
import org.hocate.tools.TZip;

/**
 * Http 报文解析类
 * @author helyho
 *
 */
public class HttpPacketParser {
	
	/**
	 * 解析协议信息
	 * 		http 头的第一行
	 * @param protocolLine
	 * @throws UnsupportedEncodingException 
	 */
	private static Map<String, Object> parseProtocol(String protocolLine) throws UnsupportedEncodingException{
		Map<String, Object> protocol = new HashMap<String, Object>();
		//请求方法
		String[] lineSplit = protocolLine.split(" ");
		if(protocolLine.indexOf("HTTP/")!=0){
			protocol.put("FL_Method", lineSplit[0]);
			//请求路径和请求串
			String[] pathSplit = lineSplit[1].split("\\?");
			protocol.put("FL_Path", URLDecoder.decode(pathSplit[0],"UTF-8"));
			if(pathSplit.length==2){
				protocol.put("value", pathSplit[1].getBytes());
			}
			//协议和协议版本
			String[] protocolSplit= lineSplit[2].split("/");
			protocol.put("FL_Protocol", protocolSplit[0]);
			protocol.put("FL_Version", protocolSplit[1]);
		}else if(protocolLine.indexOf("HTTP/")==0){
			String[] protocolSplit= lineSplit[0].split("/");
			protocol.put("FL_Protocol", protocolSplit[0]);
			protocol.put("FL_Version", protocolSplit[1]);
			protocol.put("FL_Status", lineSplit[1]);
			protocol.put("FL_StatusCode", lineSplit[2]);
		}
		return protocol;
	}
	
	/**
	 * 解析 HTTP Header属性行
	 * @param propertyLine
	 * @return
	 */
	private static Map<String,String> parsePropertyLine(String propertyLine){
		Map<String,String> property = new HashMap<String, String>();
		String[] propertySplit = propertyLine.split(": ");
		if(propertySplit.length==2){
			String propertyName = propertySplit[0];
			String properyValue = propertySplit[1];
			property.put(propertyName, properyValue);
		}
		return property;
	}
	
	/**
	 * 解析字符串中的所有等号表达式成 Map
	 * @param str
	 * @return
	 */
	public static Map<String, String> getEqualMap(String str){
		Map<String, String> equalMap = new HashMap<String, String>();
		String[] searchedStrings = TString.searchByRegex(str,"([^ ;,]+=[^ ;,]+)");
		for(String groupString : searchedStrings){
			//这里不用 split 的原因是有可能等号后的值字符串中出现等号
			String[] equalStrings = new String[2];
			int equalCharIndex= groupString.indexOf("=");
			equalStrings[0] = groupString.substring(0,equalCharIndex);
			equalStrings[1] = groupString.substring(equalCharIndex+1,groupString.length());
			if(equalStrings.length==2){
				String key = equalStrings[0];
				String value = equalStrings[1];
				if(value.startsWith("\"") && value.endsWith("\"")){
					value = value.substring(1,value.length()-1);
				}
				equalMap.put(key, value);
			}
		}
		return equalMap;
	}
	
	/**
	 * 获取HTTP 头属性里等式的值
	 * 		可以从字符串 Content-Type: multipart/form-data; boundary=ujjLiiJBznFt70fG1F4EUCkIupn7H4tzm
	 * 		直接解析出boundary的值.
	 * 		使用方法:getPerprotyEqualValue(packetMap,"Content-Type","boundary")获得ujjLiiJBznFt70fG1F4EUCkIupn7H4tzm
	 * @param propertyName
	 * @param valueName
	 * @return
	 */
	private static String getPerprotyEqualValue(Map<String,Object> packetMap,String propertyName,String valueName){
		String propertyValue = packetMap.get(propertyName).toString();//"Content-Type"
		Map<String, String> equalMap = getEqualMap(propertyValue);
		return equalMap.get(valueName); 
	}
	
	/**
	 * 处理消息的Cookie
	 * @param packetMap
	 * @param cookieLine
	 */
	@SuppressWarnings("unchecked")
	private static void addCookie(Map<String, Object> packetMap,String cookieLine){
		if(!packetMap.containsKey("Cookie")){
			packetMap.put("Cookie", new Vector<Map<String, String>>());
		}
		List<Map<String, String>> cookies = (List<Map<String, String>>) packetMap.get("Cookie");
		
		//解析 Cookie 行
		Map<String, String>cookieMap = getEqualMap(cookieLine);
		
		
		//响应 response 的 cookie 形式 一个cookie 一行
		if(cookieLine.contains("Set-Cookie")){
			//处理非键值的 cookie 属性
			if(cookieLine.toLowerCase().contains("httponly")){
				cookieMap.put("httponly", "");
			}
			if(cookieLine.toLowerCase().contains("secure")){
				cookieMap.put("secure", "");
			}
			cookies.add(cookieMap);
		}
		//请求 request 的 cookie 形式 多个cookie 一行
		else if(cookieLine.contains("Cookie")){
			for(Entry<String,String> cookieMapEntry: cookieMap.entrySet()){
				HashMap<String, String> cookieOneMap = new HashMap<String, String>();
				cookieOneMap.put(cookieMapEntry.getKey(), cookieMapEntry.getValue());
				cookies.add(cookieOneMap);
			}
		}
		
	}
	
	/**
	 * 处理 body 段
	 * 		判断是否使用 GZIP 压缩,如果使用则解压缩后返回,如果没有压缩则直接返回
	 * @param packetMap
	 * @param contentBytes
	 * @return
	 * @throws IOException
	 */
	private static byte[] dealBodyContent(Map<String, Object> packetMap,byte[] contentBytes) throws IOException{
		byte[] bytesValue = new byte[0];
		
		//是否支持 GZip
		boolean isGZip = packetMap.get("Content-Encoding")==null?false:packetMap.get("Content-Encoding").toString().contains("gzip");
		
		//如果是 GZip 则解压缩
		if(isGZip && contentBytes.length>0){
			bytesValue =TZip.decodeGZip(contentBytes);
		}
		else{
			bytesValue = TObject.nullDefault(contentBytes,new byte[0]);
		}
		return bytesValue;
	}
	
	/**
	 * 解析 HTTP 报文
	 * 		解析称 Map 形式,其中:
	 * 			1.protocol 解析成 key/value 形式
	 * 			2.header   解析成 key/value 形式
	 * 			3.cookie   解析成 List<Map<String,String>> 形式
	 * 			3.part     解析成 List<Map<Stirng,Object>>(因为是递归,参考 HTTP 解析形式) 形式
	 * 			5.body     解析成 key="value" 的Map 元素
	 * @param source
	 * @throws IOException 
	 */
	public static Map<String, Object> Parser(InputStream sourceInputStream) throws IOException{
		Map<String, Object> packetMap = new HashMap<String, Object>();

		int headerLength = 0;
		boolean isBodyConent = false;
		//按行遍历HTTP报文
		for(String currentLine = TStream.readLine(sourceInputStream);
			currentLine!=null;
			currentLine = TStream.readLine(sourceInputStream)){
			
			//空行分隔处理,遇到空行标识下面有可能到内容段
			if(currentLine.equals("")){
				//1. Method 是 null 的请求,代表是在解析 chunked 内容,则 isBodyConent = true
				//2. Method 不是 Get 方法的请求,代表有 body 内容段,则 isBodyConent = true
				if(packetMap.get("Method")==null || !packetMap.get("Method").equals("GET")){
					isBodyConent = true;
				}
			}
			//解析 HTTP 协议行
			if(!isBodyConent && currentLine.contains("HTTP")){
				packetMap.putAll(parseProtocol(currentLine));
			}
			
			//处理 cookie 和 header
			if(!isBodyConent){
				if(currentLine.contains("Cookie")){
					addCookie(packetMap,currentLine);
				}else{
					packetMap.putAll(parsePropertyLine(currentLine));
				}
			}
			
			
			//解析 HTTP 请求 body
			if(isBodyConent){
				
				String contentType =packetMap.get("Content-Type")!=null?packetMap.get("Content-Type").toString():"";
				String transferEncoding = packetMap.get("Transfer-Encoding")==null?"":packetMap.get("Transfer-Encoding").toString();
								
				//1. 解析 HTTP 的 POST 请求 body 参数
				if(contentType.contains("application/x-www-form-urlencoded")){
					byte[] value = dealBodyContent(packetMap, TStream.readAll(sourceInputStream));
					packetMap.put("value", value);
				}
				
				//2. 解析 HTTP 的 POST 请求 body part
				else if(contentType.contains("multipart/form-data")){
					//用来保存 Part 的 list
					ArrayList<Map<String, Object>> bodyPartList = new ArrayList<Map<String, Object>>();
					
					//取boundary 用于 part 内容分段
					String boundary = HttpPacketParser.getPerprotyEqualValue(packetMap,"Content-Type","boundary");
					
					for(byte[] spliteBytes = TStream.read(sourceInputStream, ("--"+boundary).getBytes());
							sourceInputStream.available()>0;
							spliteBytes = TStream.read(sourceInputStream, ("--"+boundary).getBytes())){
						
						if(spliteBytes!=null){
							spliteBytes = Arrays.copyOfRange(spliteBytes, 2, spliteBytes.length-2);
							//递归调用 pareser 方法解析
							Map<String, Object> partMap = Parser(new ByteArrayInputStream(spliteBytes));
							//对Content-Disposition中的"name=xxx"进行处理,方便直接使用
							Map<String, String> contentDispositionValue = HttpPacketParser.getEqualMap(partMap.get("Content-Disposition").toString());
							partMap.putAll(contentDispositionValue);
							//加入bodyPartList中
							bodyPartList.add(partMap);
						}
						
					}
					//将存有多个 part 的 list 放入packetMap
					packetMap.put("Parts", bodyPartList);
				}
				
				//3. 解析 HTTP 响应 body 内容段的 chunked 
				else if(transferEncoding.equals("chunked")){
					
					byte[] chunkedBytes = new byte[0];
					for(String chunkedLengthLine = TStream.readLine(sourceInputStream);
							chunkedLengthLine!=null && !chunkedLengthLine.equals("0");
							chunkedLengthLine = TStream.readLine(sourceInputStream)){
						
						//读取chunked长度
						int chunkedLength = Integer.parseInt(chunkedLengthLine,16);
						
						//按长度读取chunked内容
						byte[] chunkedPartBytes  = TStream.read(sourceInputStream,chunkedLength);
						
						//如果多次读取则拼接
						chunkedBytes = TStream.byteArrayConcat(chunkedBytes, chunkedBytes.length, chunkedPartBytes, chunkedPartBytes.length);
						
						//跳过换行符号
						sourceInputStream.read();
						sourceInputStream.read();
					}
					byte[] value = dealBodyContent(packetMap, chunkedBytes);
					packetMap.put("value", value);
				}
				//4. HTTP(请求和响应) 报文的内容段中Content-Length 提供长度,按长度读取 body 内容段
				else if(packetMap.containsKey("Content-Length")){
					byte[] contentBytes = new byte[0];
					int contentLength = Integer.parseInt(packetMap.get("Content-Length").toString());
					contentBytes = TStream.read(sourceInputStream,contentLength);
					byte[] value = dealBodyContent(packetMap, contentBytes);
					packetMap.put("value", value);
				}
				//5. 容错,没有标识长度则默认读取全部内容段
				else if(packetMap.get("value")==null || packetMap.get("value").equals("")){
					byte[] contentBytes = new byte[0];
					contentBytes = TStream.readAll(sourceInputStream);
					byte[] value = dealBodyContent(packetMap, contentBytes);
					packetMap.put("value", value);
				}
				break;
			}
			if(!isBodyConent){
				headerLength = headerLength+currentLine.length()+2;
			}
		}
		
		sourceInputStream.close();
		return packetMap;
	}
	
	/**
	 * 解析报文成 HttpRequest 对象
	 * @param inputStream
	 * @return
	 * @throws IOException
	 */
	@SuppressWarnings("unchecked")
	public static HttpRequest parseRequest(InputStream inputStream) throws IOException{
		HttpRequest request = new HttpRequest();
		
		Map<String, Object> parsedPacket = Parser(inputStream);
		
		//填充报文到请求对象
		Set<Entry<String, Object>> parsedItems= parsedPacket.entrySet();
		for(Entry<String, Object> parsedPacketEntry: parsedItems){
			String key = parsedPacketEntry.getKey();
			switch (key) {
			case "FL_Method":
				request.protocol().setMethod(parsedPacketEntry.getValue().toString());
				break;
			case "FL_Protocol":
				request.protocol().setProtocol(parsedPacketEntry.getValue().toString());
				break;
			case "FL_Version":	
				request.protocol().setVersion(Float.valueOf(parsedPacketEntry.getValue().toString()));
				break;
			case "FL_Path":	
				request.protocol().setPath(parsedPacketEntry.getValue().toString());
				break;
			case "Cookie":
				List<Map<String, String>> cookieMap = (List<Map<String, String>>)parsedPacket.get("Cookie");
				//遍历 Cookie,并构建 Cookie 对象
				for(Map<String,String> cookieMapItem : cookieMap){
					Cookie cookie = Cookie.buildCookie(cookieMapItem);
					request.cookies().add(cookie);
				}
				break;
			case "value":
				byte[] value = (byte[])(parsedPacketEntry.getValue());
				//如果是 GET 请求,则分析出来的内容(parsedPacket)中的 value 是 QueryString
				if(parsedPacket.get("FL_Method").equals("GET")){
					request.protocol().setQueryString(new String(value,"UTF-8"));
				}
				else{
					request.body().writeBytes(value);
				}
				break;
			case "Parts":
				List<Map<String, Object>> parsedParts = (List<Map<String, Object>>)(parsedPacketEntry.getValue());
				//遍历 part List,并构建 Part 对象
				for(Map<String, Object> parsedPartMap : parsedParts){
					Part part = new Part();
					//将 part Map中的值,并填充到新构建的 Part 对象中
					for(Entry<String, Object> parsedPartMapItem : parsedPartMap.entrySet()){
						//填充 Value 中的值到 body 中
						if(parsedPartMapItem.getKey().equals("value")){
							part.body().writeBytes(TObject.cast(parsedPartMapItem.getValue()));
						}else{
							//填充 header
							part.header().put(parsedPartMapItem.getKey(), parsedPartMapItem.getValue().toString());
						}
					}
					request.parts().add(part);
				}
				break;
			default:
				request.header().put(parsedPacketEntry.getKey(), parsedPacketEntry.getValue().toString());
				break;
			}
		}
		
		return request;
	}
	
	/**
	 * 解析报文成 HttpResponse 对象
	 * @param inputStream
	 * @return
	 * @throws IOException
	 */
	@SuppressWarnings("unchecked")
	public static HttpResponse parseResponse(InputStream inputStream) throws IOException{
		HttpResponse response = new HttpResponse();
		
		Map<String, Object> parsedPacket = Parser(inputStream);
		
		//填充报文到响应对象
		Set<Entry<String, Object>> parsedItems= parsedPacket.entrySet();
		for(Entry<String, Object> parsedPacketEntry: parsedItems){
			String key = parsedPacketEntry.getKey();
			switch (key) {
			case "FL_Protocol":
				response.protocol().setProtocol(parsedPacketEntry.getValue().toString());
				break;
			case "FL_Version":	
				response.protocol().setVersion(Float.valueOf(parsedPacketEntry.getValue().toString()));
				break;
			case "FL_Status":	
				response.protocol().setStatus(Integer.valueOf(parsedPacketEntry.getValue().toString()));
				break;
			case "FL_StatusCode":	
				response.protocol().setStatusCode(parsedPacketEntry.getValue().toString());
				break;
			case "Cookie":
				List<Map<String, String>> cookieMap = (List<Map<String, String>>)parsedPacketEntry.getValue();
				//遍历 Cookie,并构建 Cookie 对象
				for(Map<String,String> cookieMapItem : cookieMap){
					Cookie cookie = Cookie.buildCookie(cookieMapItem);
					response.cookies().add(cookie);
				}
				break;
			case "value":
				response.body().writeBytes(TObject.cast(parsedPacketEntry.getValue()));
				break;
			default:
				response.header().put(parsedPacketEntry.getKey(), parsedPacketEntry.getValue().toString());
				break;
			}
		}
		return response;
	}
	
	public static void main(String[] args) throws IOException {
		String httpRequestString = 
		 
//				"POST /test/t HTTP/1.1\r\n"+
//				"Connection: keep-alive\r\n"+
//				"Content-Type: application/x-www-form-urlencoded\r\n"+
//				"Content-Length: 34\r\n"+
//				"User-Agent: Jakarta Commons-HttpClient/3.1\r\n"+
//				"Host: 127.0.0.1:1031\r\n"+
//				"\r\n"+
//				"name=helyho&age=32%3D&address=wlmq\r\n"+
//				"\r\n";
			
//				"GET /test/t HTTP/1.1\r\n"+
//				"Connection: keep-alive\r\n"+
//				"UserAgent: Jakarta Commons-HttpClient/3.1\r\n"+
//				"Host: 127.0.0.1:1031\r\n"+
//				"\r\n";
	
				"POST /test/t HTTP/1.1\r\n"+
				"Connection: keep-alive\r\n"+
				"Content-Type: multipart/form-data; boundary=ujjLiiJBznFt70fG1F4EUCkIupn7H4tzm\r\n"+
				"Content-Length: 329\r\n"+
				"User-Agent: Jakarta Commons-HttpClient/3.1\r\n"+
				"Cookie: BAIDUID=57939E50D6B2A0B23D20CA330C89E290:FG=1; BAIDUPSID=57939E50D6B2A0B23D20CA330C89E290;\r\n"+
				"Host: 127.0.0.1:1031\r\n"+
				"\r\n"+
				"--ujjLiiJBznFt70fG1F4EUCkIupn7H4tzm\r\n"+
				"Content-Disposition: form-data; name=\"name\"; filename=\"hh.jpg\"\r\n"+
				"\r\n"+
				"helyho\r\n"+
				"--ujjLiiJBznFt70fG1F4EUCkIupn7H4tzm\r\n"+
				"Content-Disposition: form-data; name=\"age\"\r\n"+
				"\r\n"+
				"32\r\n"+
				"--ujjLiiJBznFt70fG1F4EUCkIupn7H4tzm\r\n"+
				"Content-Disposition: form-data; name=\"address\"\r\n"+
				"\r\n"+
		 		"wlmq\r\n"+
				"--ujjLiiJBznFt70fG1F4EUCkIupn7H4tzm--\r\n\r\n";
				
		Logger.simple(new String(HttpPacketParser.parseRequest(new ByteArrayInputStream(httpRequestString.getBytes())).asBytes()));
	}
}

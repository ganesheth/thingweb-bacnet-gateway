package ethz.ganeshr.wot.test.BACnet;

public class BACnetChannelParam
{
	private String ipaddr;
	private int port;
	public BACnetChannelParam(String ipaddr, int port){
		this.ipaddr = ipaddr;
		this.port = port;
	}
	
	public String getIpAddress(){
		return ipaddr;
	}
	
	public int getPort(){
		return port;
	}
}

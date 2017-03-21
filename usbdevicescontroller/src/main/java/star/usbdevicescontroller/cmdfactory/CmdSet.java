package star.usbdevicescontroller.cmdfactory;

/**
 * 命令交互流程
 * 1 -> write head1 长度16
 * 2 -> write cmd 长度20（实际操作命令）
 * 3 -> read data[200]
 * 4 -> write head2 长度16
 * 5 -> read data[200] （实际返回数据在这里）
 * 6 -> read data[200]
 *
 * Created by Star on 2017/3/6.
 */
public class CmdSet {
	public static final char CMD_HEAD1[] = {0x45,0x3a,0x02,0x67,0x23,0x01,0x00,0x00,0x14,0x00,0x00,0x00,0xba,0xc5,0xfd,0x98}; // 16
	public static final char CMD_HEAD2[] = {0x45,0x3a,0x02,0x67,0xbc,0x0a,0x00,0x00,0x14,0x00,0x00,0x00,0xba,0xc5,0xfd,0x98}; // 16

	public static final char CMD_LOGIN[] = new char[] {0x4f, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}; // 20
	public static final char CMD_MASS_STORAGE[] = new char[] {0x39, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}; // 20
}

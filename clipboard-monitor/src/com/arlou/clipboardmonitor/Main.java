package com.arlou.clipboardmonitor;

import static java.lang.System.out;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HMODULE;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.LPARAM;
import com.sun.jna.platform.win32.WinDef.LRESULT;
import com.sun.jna.platform.win32.WinDef.WPARAM;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.platform.win32.WinUser.MSG;
import com.sun.jna.platform.win32.WinUser.WNDCLASSEX;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

public class Main {

	private static final class WindowProc implements WinUser.WindowProc {

		private HWND nextViewer;
		private Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();

		public void setNextViewer(HWND nextViewer) {
			this.nextViewer = nextViewer;
		}

		public LRESULT callback(HWND hWnd, int uMsg, WPARAM wParam, LPARAM lParam) {
			// System.out.println("#callback : uMsg=" + uMsg);
			switch (uMsg) {
			case User32x.WM_CHANGECBCHAIN:
				// If the next window is closing, repair the chain.
				System.out.println("Repairing clipboard viewers chain...");
				if (nextViewer.toNative().equals(wParam.toNative())) {
					nextViewer = new HWND(Pointer.createConstant(lParam.longValue()));
				} else if (nextViewer != null) {
					User32x.INSTANCE.SendMessage(nextViewer, uMsg, wParam, lParam);
				}
				return new LRESULT(0);
			case User32x.WM_DRAWCLIPBOARD:
				try {
					System.out.println("Clipboard data: " + clipboard.getData(DataFlavor.stringFlavor));
				} catch (UnsupportedFlavorException | IOException e) {
					e.printStackTrace();
				}
				break;
			}
			return User32.INSTANCE.DefWindowProc(hWnd, uMsg, wParam, lParam);
		}
	}

	public interface User32x extends StdCallLibrary {

		User32x INSTANCE = (User32x) Native.loadLibrary("user32", User32x.class, W32APIOptions.UNICODE_OPTIONS);

		final int WM_DESTROY = 0x0002;
		final int WM_CHANGECBCHAIN = 0x030D;
		final int WM_DRAWCLIPBOARD = 0x0308;

		HWND SetClipboardViewer(HWND viewer);

		void SendMessage(HWND nextViewer, int uMsg, WPARAM wParam, LPARAM lParam);

		void ChangeClipboardChain(HWND viewer, HWND nextViewer);

		int SetWindowLong(HWND hWnd, int nIndex, WinUser.WindowProc callback);

		int MsgWaitForMultipleObjects(int length, HANDLE[] handles, boolean b, int infinite, int qsAllinput);

	}

	public static void main(String[] args) throws IOException {
		WString windowClass = new WString("MyWindowClass");
		HMODULE hInst = Kernel32.INSTANCE.GetModuleHandle("");
		WNDCLASSEX wClass = new WNDCLASSEX();
		wClass.hInstance = hInst;
		WindowProc wProc = new WindowProc();
		wClass.lpfnWndProc = wProc;
		wClass.lpszClassName = windowClass;

		// register window class
		User32.INSTANCE.RegisterClassEx(wClass);
		getLastError();

		// create new window
		HWND hWnd = User32.INSTANCE.CreateWindowEx(User32.WS_EX_TOPMOST, windowClass,
				"My hidden helper window, used only to catch the windows events", 0, 0, 0, 0, 0, null, // WM_DEVICECHANGE
																										// contradicts
																										// parent=WinUser.HWND_MESSAGE
				null, hInst, null);
		getLastError();
		System.out.println("Window created hwnd: " + hWnd.getPointer().toString());

		// set clipboard viewer
		HWND nextViewer = User32x.INSTANCE.SetClipboardViewer(hWnd);
		wProc.setNextViewer(nextViewer);

		// pump messages
		MSG msg = new MSG();
		while (User32.INSTANCE.GetMessage(msg, hWnd, 0, 0) != 0) {
			User32.INSTANCE.TranslateMessage(msg);
			User32.INSTANCE.DispatchMessage(msg);
		}
		// wait for input
		System.in.read();
		// destroy window
		User32.INSTANCE.UnregisterClass(windowClass, hInst);
		User32.INSTANCE.DestroyWindow(hWnd);
		System.exit(0);
	}

	public static int getLastError() {
		int rc = Kernel32.INSTANCE.GetLastError();
		if (rc != 0)
			out.println("error: " + rc);
		return rc;
	}

}

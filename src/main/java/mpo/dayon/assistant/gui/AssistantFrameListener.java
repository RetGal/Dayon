package mpo.dayon.assistant.gui;

import mpo.dayon.common.event.Listener;

public interface AssistantFrameListener extends Listener {
	void onMouseMove(int x, int y);

	void onMousePressed(int x, int y, int button);

	void onMouseReleased(int x, int y, int button);

	void onMouseWheeled(int x, int y, int rotations);

	void onKeyPressed(int keyCode, char keyChar);

	void onKeyReleased(int keyCode, char keyChar);
}

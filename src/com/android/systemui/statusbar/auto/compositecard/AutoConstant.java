package com.android.systemui.statusbar.auto.compositecard;

public class AutoConstant {
    public static final String AUTONAVI_SEND_ACTION = "AUTONAVI_STANDARD_BROADCAST_SEND";
    public static final String AUTONAVI_RECV_ACTION = "AUTONAVI_STANDARD_BROADCAST_RECV";
    public static final String AUTONAVI_KEY_TYPE = "KEY_TYPE";

	/*
	 */
	public static class AUTONAVI_STATE {
		/*
		 */
		public static final int START_RUN = 0;
		/*
		 */
		public static final int INITED = 1;
		/*
		 */
		public static final int STOP_RUN = 2;
		/*
		 */
		public static final int ENTER_FOREGROUND = 3;
		/*
		 */
		public static final int ENTER_BACKGROUND = 4;
		/*
		 */
		public static final int START_COMPUTE = 5;
		/*
		 */
		public static final int COMPUTE_SUCCESS = 6;
		/*
		 */
		public static final int COMPUTE_FAIL = 7;
		/*
		 */
		public static final int START_NAVI = 8;
		/*
		 */
		public static final int STOP_NAVI = 9;
		/*
		 */
		public static final int START_SIMULATION = 10;
		/*
		 */
		public static final int PAUSE_SIMULATION = 11;
		/*
		 */
		public static final int STOP_SIMULATION = 12;
		/*
		 */
		public static final int START_TTS = 13;
		/*
		 */
		public static final int STOP_TTS = 14;
	}
	
	public static class GuideInfoExtraKey {
		/*
		 */
		public static final String TYPE = "TYPE";
		/*
		 */
		public static final String CUR_ROAD_NAME = "CUR_ROAD_NAME";
		/*
		 */
		public static final String NEXT_ROAD_NAME = "NEXT_ROAD_NAME";
		/*
         */
		public static final String SAPA_DIST = "SAPA_DIST";
		/*
		 */
		public static final String SAPA_TYPE = "SAPA_TYPE";
		/*
		 */
		public static final String CAMERA_DIST = "CAMERA_DIST";
		/*
		 */
		public static final String CAMERA_TYPE = "CAMERA_TYPE";
		 /*
          */
		public static final String CAMERA_SPEED = "CAMERA_SPEED";
		/*
		 */
		public static final String CAMERA_INDEX = "CAMERA_INDEX";
		/*
		 */
		public static final String ICON = "ICON";
		/*
		 */
		public static final String ROUTE_REMAIN_DIS = "ROUTE_REMAIN_DIS";
		/*
		 */
		public static final String ROUTE_REMAIN_TIME = "ROUTE_REMAIN_TIME";
		/*
		 */
		public static final String SEG_REMAIN_DIS = "SEG_REMAIN_DIS";
		/*
		 */
		public static final String SEG_REMAIN_TIME = "SEG_REMAIN_TIME";
		/*
		 */
		public static final String CAR_DIRECTION = "CAR_DIRECTION";
		/*
		 */
		public static final String CAR_LATITUDE = "CAR_LATITUDE";
		/*
		 */
		public static final String CAR_LONGITUDE = "CAR_LONGITUDE";
		/*
		 */
		public static final String LIMITED_SPEED = "LIMITED_SPEED";
	}

}

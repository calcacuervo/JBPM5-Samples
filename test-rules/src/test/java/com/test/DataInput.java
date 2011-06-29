package com.test;

import java.util.Map;

public class DataInput {
	private Map<String, Object> dataMap;

	public DataInput(Map<String, Object> dataMap) {
		this.dataMap = dataMap;
	}

	public Map<String, Object> getDataMap() {
		return dataMap;
	}

	public void setDataMap(Map<String, Object> dataMap) {
		this.dataMap = dataMap;
	}

}

package com.test;

import java.io.Serializable;
import java.util.Map;

public class DataInput implements Serializable{
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

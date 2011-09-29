package com.john.websphere;

import java.util.List;

public class StackViewChangeCollection implements Comparable<StackViewChangeCollection>{
	
	private String stackName;
	private List<String> viewChanges;
	
	
	public StackViewChangeCollection(){
		
	}
	
	public StackViewChangeCollection(String s, List<String> l){
		this.setStackName(s);
		this.setViewChanges(l);
	}
	
	
	public String getStackName() {
		return stackName;
	}
	public void setStackName(String stackName) {
		this.stackName = stackName;
	}
	public List<String> getViewChanges() {
		return viewChanges;
	}
	public void setViewChanges(List<String> viewChanges) {
		this.viewChanges = viewChanges;
	}
	
	public String startOfTimeSpan(){
		return getViewChanges().get(0).substring(0, 25);
		
		
		
	}
	
	public String endOfTimeSpan(){
		//GWT.log("end datetime is " + getViewChanges().get(getViewChanges().size()-1).substring(0, 25), null);
		return getViewChanges().get(getViewChanges().size()-1).substring(0, 25);
		
		
	}

	public int compareTo(StackViewChangeCollection svcc) {
		int results = svcc.getStackName().compareTo(this.getStackName());
		if (results > 0){
			return results;
		} else if (results < 0){
			return results;
		} else {
			return 0;
		}
		
	}
	
	

}

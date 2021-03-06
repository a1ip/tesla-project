package ru.teslaprj.syntax;

import java.math.BigInteger;

public class LogicalVariable
{
	public enum Status
	{
		SIGNATURE_RESULT, // ���������� ������������ � ���������, �������� ������������
		SIGNATURE_READONLY, // ���������� ������������ � ���������, �������� ������������
		OPTIONAL, // ���������� ������������ � ���������, �� �� �������� ������������
		LOCAL // ���������� �� ������������ � ���������, � ���������� � ���� ����������
	}
	
	private String baseName;
	private String currentName;
	private int currentVersion = 0;
	public int size;
	private BigInteger value = null; 
	private final Status status;
	
	public LogicalVariable( final String name, final int size, final Status status )
	{
		assert name != null;
		assert ! name.equals("");
		baseName = name;
		this.size = size;
		nextVersion();
		this.status = status;
	}
	
	void nextVersion()
	{
		currentVersion++;
		currentName = "_" + currentVersion + baseName;
	}
	
	String current()
	{
		return currentName;
	}
	
	String next()
	{
		return "_" + (currentVersion+1) + baseName;
	}
	
	/** _1 + base name */
	String getBaseName()
	{
		return "_1" + baseName;
	}
	
	/** ���, ��� ��� �������� ������������� */
	public String getCanonicalName()
	{
		return baseName;
	}
	
	public BigInteger getValue() 
	{
		return value;
	}

	public void setValue( final BigInteger value) 
	{
		this.value = value;
	}
	
	public int getBitlen()
	{
		return size;
	}	

	public Status getStatus()
	{
		return status;
	}
}
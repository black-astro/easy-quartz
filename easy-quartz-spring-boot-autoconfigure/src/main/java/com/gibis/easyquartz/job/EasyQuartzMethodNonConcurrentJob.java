package com.gibis.easyquartz.job;

import org.quartz.DisallowConcurrentExecution;

@DisallowConcurrentExecution
public class EasyQuartzMethodNonConcurrentJob extends EasyQuartzMethodJob {}

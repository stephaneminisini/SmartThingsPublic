/**
 *  Home Alarm Server
 *
 *  Copyright 2016 Stephane Minisini
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
definition(
    name: "Home Alarm Server",
    namespace: "itcsm.alarmserver",
    author: "Stephane Minisini",
    description: "Service Manager for Alarm Server",
    category: "",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png")


preferences {
	page(name:"AlarmServerDiscovery", title:"Alarm Server Setup", content:"AlarmServerDiscovery", refreshTimeout:3)
	section("Title") {
		// TODO: put inputs here
	}
}

def installed() {
	log.debug "Installed with settings: ${settings}"

	initialize()
}

def updated() {
	log.debug "Updated with settings: ${settings}"

	unsubscribe()
	initialize()
}

def initialize() {
	// TODO: subscribe to attributes, devices, locations, etc.
	unsubscribe()
    state.subscribed = false
    
    log.debug "Initializing"
    if (selectedServerController)
    {
    	log.debug "Selected an Alarm Server, Adding the device"
    	addAlarmServer()
    }
}

// Discovery and Verification Process
//
def AlarmServerDiscovery()
{
	log.debug("In the alarm server discovery page")
    
    int discoveryRefreshCount = !state.discoveryRefreshCount ? 0 : state.discoveryRefreshCount as int
    state.discoveryRefreshCount = discoveryRefreshCount + 1
    def refreshInterval = 3
    
    def options = DeviceDiscovered() ?: [:]
    def numFound = options.size() ?: 0
    
    if (!state.subscribed)
    {
    	log.debug("Subscribing to the location")
    	subscribe(location, null, ssdpHandler, [filterevents:false])
        state.subscribed = true
    }
    
    if ((discoveryRefreshCount % 8) == 0) 
    {    
    	log.debug("Trying to discover the device")
    	discoverAlarmServer()
    }
    
    return dynamicPage(name:"AlarmServerDiscovery", title:"Discovery Started!", nextPage:"", refreshInterval:refreshInterval, install:true, uninstall: true) {
            section("Please wait while we discover your Alarm Server. Discovery can take five minutes or more, so sit back and relax! Select your device below once discovered.") {
                input "selectedServerController", "enum", required:false, title:"Select Alarm Server (${numFound} found)", multiple:true, options:options
            }
    }
}

private discoverAlarmServer()
{
	log.debug("Sending lan discovery")
	sendHubCommand(new physicalgraph.device.HubAction("lan discovery urn:schemas-upnp-org:device:alarmserver:1", physicalgraph.device.Protocol.LAN))
}

def DeviceDiscovered()
{
	def Devices = getDevices()
    def map = [:]
    devices.each {
    	log.debug "Setting device: " + it.value.IP + ":" + it.value.port
    	def value = it.value.name ?: "Alarm Server"
        def key = it.value.IP + ":" + it.value.port
        map["${key}"] = value
    }
    
    map
}

def ssdpHandler(evt)
{
	def description = evt.description
    def hub = evt?.hubId
	log.debug("SSDP Handler description= " + description)
    
    def parsedEvent = parseEventMessage(description)
    parsedEvent << ["hub":hub]

	if (parsedEvent?.ssdpterm?.contains('itcsm:alarmserver'))
    {
    	log.debug('SSDP Found device: ' + parsedEvent?.usn)
        def devices = getDevices()
        devices << ["${parsedEvent.usn.toString()}":parsedEvent]
    }
}

private def parseEventMessage(msg)
{
	def event = [:]
    def parts = msg.split(',')
    parts.each { part ->
    	log.debug('Part= ' + part)
        part = part.trim()
        
        if (part.toLowerCase().startsWith('devicetype:'))
        {
        	if (part.indexOf(':') != -1)
            {
        		def valueString = part.substring(part.indexOf(':') + 1)
	            if (valueString)
    	        {
        			event.devicetype = valueString
            	    log.debug('devicetype= ' + event.devicetype)
            	}
            }
        }
        else if (part.toLowerCase().startsWith('mac:'))
        {
        	if (part.indexOf(':') != -1)
            {
        		def valueString = part.substring(part.indexOf(':') + 1)
        	   	if (valueString)
            	{
            		event.macaddress = valueString
                	log.debug('macaddress= ' + event.macaddress)
            	}
            }
        }
        else if (part.toLowerCase().startsWith('networkaddress'))
        {
        	if (part.indexOf(':') != -1)
            {
        		def valueString = part.substring(part.indexOf(':') + 1)
	            if (valueString)
    	        {
        			event.IP = valueString
            	    log.debug('IP= ' + event.IP)
            	}
            }
        }
        else if (part.toLowerCase().startsWith('deviceaddress'))
        {
        	if (part.indexOf(':') != -1)
            {
        		def valueString = part.substring(part.indexOf(':') + 1)
        	    if (valueString)
            	{
        			event.port = valueString
                	log.debug('Port= ' + event.port)
            	}
            }
        }
        else if (part.toLowerCase().startsWith('stringcount'))
        {
        	if (part.indexOf(':') != -1)
            {
        		def valueString = part.substring(part.indexOf(':') + 1)
        	    if (valueString)
            	{
        			event.stringcount = valueString
            	}
            }
        }
        else if (part.toLowerCase().startsWith('ssdppath'))
        {
        	if (part.indexOf(':') != -1)
            {
        		def valueString = part.substring(part.indexOf(':') + 1)
	            if (valueString)
    	        {
        			event.verifypath = valueString
            	    log.debug('VerifyPath= ' + event.verifypath)
            	}
            }
        }
        else if (part.toLowerCase().startsWith('ssdpusn'))
        {
        	if (part.indexOf(':') != -1)
            {
        		def valueString = part.substring(part.indexOf(':') + 1)
            	if (valueString)
            	{
        			event.usn = valueString
                	log.debug('USN= ' + event.usn)
            	}
            }
        }
        else if (part.toLowerCase().startsWith('ssdpterm'))
        {
			if (part.indexOf(':') != -1)
            {
        		def valueString = part.substring(part.indexOf(':') + 1)
            	if (valueString)
            	{
        			event.ssdpterm = valueString
                	log.debug('ssdpterm= ' + event.ssdpterm)
            	}
            }
        }
    }
    
    event
}

// Adding the Server
//
def addAlarmServer()
{
	def devices = getDevices()
    selectedServerController.each { dni ->
    	log.debug "Adding $dni"
    	def d = getChildDevice(dni)
        if (!d)
        {
     		def newAlarmSrv = devices.find { (it.value.IP + ":" + it.value.port) == dni }
            d = addChildDevice("itcsm", "Alarm Server", dni, newAlarmSrv?.value?.hub, [label: "Alarm Server", "IP": newAlarmSrv.value.IP, "port": newAlarmSrv.value.port ])
            log.debug "created ${d.displayname} with id $dni"
        }
        else
        {
        	log.debug "Found ${d.displayname} with id $dni - already exists"
        }
        
    }
}

// Utility Functions
//
def getDevices()
{
	state.devices = state.devices ?: [:]
}

private Integer convertHexToInt(hex) 
{
    Integer.parseInt(hex,16)
}

private String convertHexToIP(hex) 
{
    [convertHexToInt(hex[0..1]),convertHexToInt(hex[2..3]),convertHexToInt(hex[4..5]),convertHexToInt(hex[6..7])].join(".")
}

private getHostAddress(d) 
{
    def parts = d.split(":")
    def ip = convertHexToIP(parts[0])
    def port = convertHexToInt(parts[1])
    return ip + ":" + port
}
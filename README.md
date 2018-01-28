# NFC (Near Field Communication) Ticket Application Java

An Android application designed for reading and writing to NFC (Near Field Communication) cards. This application is aimed at the secure issuing and usage of tickets (in this scenario a ski-resort), on Ultralight C smartcards. It focuses on the concepts of Network Security and implements best practices accordingly.

## Application Features


### Issue Ticket

```
	1. Authenticate()
	2. Check state()
	3. if state==USED
		(a) Increment ticket max
		(b) Increment Date
		(c) Update HMAC
	4. else if state==NOT USED
		(a) Increment ticket max
		(b) Update HMAC
	5. else if state==EXPIRED
		(a) Increment ticket max
		(b) Set Date = 0
		(c) Update HMAC
```

### Use Ticket

```
	1. Authenticate()
	2. Check state()
	3. if state==USED && counter < ticket max
		(a) Increment Counter
	4. else if state=NOT USED
		(a) Set Date
		(b) Set HMAC
	5. else if state=EXPIRED || state=USED
		(a) Refuse
```

### Check Ticket State (Expiry)

```
	1. Verify card HMAC=HMAC(uid | ticket count | date)
	2. if date==0 return NOT USED
	3. else if today - date≤30 return USED
	4. else return EXPIRED
```

## Pre-requisites

What things you need to install

```
1. Android SDK version 4.0.1 or greater
2. Android API verison 14 or greater
3. Java (SE) 

```

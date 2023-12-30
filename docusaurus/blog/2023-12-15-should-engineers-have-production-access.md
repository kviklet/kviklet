---
slug: "should-engineers-have-production-access"
title: "Should Engineers have production access?"
publishedAt: 2023-12-15
description: "Devops and Agile vs Compliance"
isPublish: true
authors: [jascha]
tags: [Production Access, DevOps, DevSecOps, Engineering Culture]
---

## Should Engineers Have Production Access?

I asked myself this question on my first job at Scalable Capital, 4 years ago. I started at a FinTech company with somewhere between 50 and 100 engineers. I was enthusiastic about DevOps and a **You build it, you run it mindset**.  
But reality hit hard. You can't give every engineer full production access, and justify lax credential management with _I want to give people **ownership and trust**_.

_Yes_, you want to hire smart people as a leader, and you need to trust your engineers for them to be productive. But even if your hiring is perfect and you never hire anyone that's malicious, the **greatest minds make mistakes**.  
And this doesn't have to be leaking a credential or leaving their Macbook open at a Starbucks. This can even be running the `DROP TABLE;` statement on the production database instead of dev.
Yes this happens in real companies like [GitLab](https://about.gitlab.com/blog/2017/02/10/postmortem-of-database-outage-of-january-31/).

_But_ I hear you say, _for an engineer to do their job and feel responsible for their part of the system, aka "own" it, They **need access to production**._  
And you are perfectly right in that assessment.

1. If production is down
2. A bug has to be investigated
3. Even just a quarterly tax report still isn't automated...

Someone has to log in to prod and figure out how to solve it.
<!--truncate-->
---

A **common approach** is to have an Operations Team handle such topics, or even let the SRE team do it. But this usually **slows resolution of problems down**, since information first has to travel from one team to the other.  
And even worse than that, it **splits ownership** which goes very much against the core of a modern devops culture (or, more recently platform engineering).
And in fact does this really solve the problem if your SRE folks can still accidentally install malware?
This happened at [LastPass earlier this year](https://blog.lastpass.com/2023/03/security-incident-update-recommended-actions/):

> Incident 2 Summary: The threat actor targeted a senior DevOps engineer by exploiting vulnerable third-party software. The threat actor leveraged the vulnerability to deliver malware, bypass existing controls, and ultimately gain unauthorized access to cloud backups. The data accessed from those backups included system configuration data, API secrets, third-party integration secrets, and encrypted and unencrypted LastPass customer data.

### So what's the solution?

Do you have to **decide between a modern, agile workflow and security?** Can you really not have both?  
To a degree, it is often indeed a tradeoff that is made. Introducing more processes, to at least achieve compliance.But seldom do these processes really make the system more secure, resulting in a sort of **security theater**.

The **alternative is expensive**, it involves building up an **internal toolstack** that allows engineers to safely access production directly. This is in fact what larger companies often end up doing.  
I talked to Engineers at AWS and Azure, and both have fully fleshed-out internal applications that **allow engineers to shadow each other's sessions** when they access production resources. Allowing to pull the plug on each other in case someone attempts something shady.

Core Features of a solution like that should be:

- **SSO login**: So no credentials are shared
- **Extensive Audit trailing**: So at least if something goes wrong you have a trace
- **4-Eyes Principle**: This is the best measure to actually prevent mistakes **and** malicious attacks. _(I'm a huge fan of this mechanism and will write another post about it eventually)_

_But this sounds quite generic, why is there no prebuilt solution out there for this task?_  
Well there is a few, Most notably [Teleport](https://github.com/gravitational/teleport) which at least in the enterprise version could tick all the boxes. But it is also incredibly expensive as well as complex to set up.

---

So if you're just as baffled as I am at why this **doesn't exist in the world** and we are left with suboptimal or even insecure practices, feel free to have a look at [Kviklet](https://kviklet.dev) which is my attempt at solving this problem once and for all.

---
slug: "Companies-building-their-products-open-source"
title: "How Do Companies Fare When Building Their Products Open Source?"
publishedAt: 2023-11-27
description: "Terraform Drama vs Gitlab Stability - Why do some companies work well with open source and others don't?"
isPublish: true
authors: [jascha]
tags: [Startup, Open Source]
---

## How Do Companies Fare When Building Their Products Open Source?

Open source is core to the modern engineering world. In one way or another, _everything runs on Linux or Android_, or at the very least, is developed with tools that are available as open source. This hasn't gone unnoticed in the startup world, companies are being founded with a core product that is open source, sometimes funded purely based on GitHub Stars with no active revenue streams.

But I wonder if this actually makes a lot of sense, or if open source isn't by design the more "communist" approach to software development where everyone can contribute and use a tool for free. Which doesn't cooperate well with the goal of building a company that wants to make money.

There have been some interesting developments in recent times with open source-based companies. In March 2022 Snowflake bought Streamlit for $800M. Streamlit at the time was a very hot data visualization tool used by data analysts to easily build interactive web pages that allowed users to explore their analysis. The project had 15.000 stars at the time of exit, and no relevant revenue that would justify this valuation.

These days that doesn't fly anymore, tech valuations have shrunk and especially if you don't have significant cash flow you're in for a hard time. Multiple projects have struggled with this. I've recently worked in the data/analytics area so I am more aware of events there but I'm sure that this is a cross industry trend.

I want to look at "Open source Companies" and how their decision to go open source has played out.
<!--truncate-->
Airbyte is a data integration platform that is partially open source-based, the initial proposition was to build a tool that can integrate all kind of data from different APIs. The connectors themselves should be truly open source while the orchestration layer was licensed.

Airbyte recently realized that this decision led to competitors using their open source connectors as well. Consequently they changed the licensing on the most important connectors (databases) so that only Airbyte themselves are allowed to offer them as a service. This didn't go great with the community and makes the licensing less transparent, making it unclear which parts of Airbyte remain truly open source.

I also believe that the initial reason for starting open source was to make use of the community. Essentially collecting free work that builds into Airbyte product. No one wants to build hundreds of connectors inhouse; it's simply too costly. I doubt this plan will work very well with connectors that are now no longer truly open source. After all, who is willing to contribute code to a company without compensation?"

![Headling of meltano enabling airbyte connectors](./meltano-headline.png)

> Surely Airbyte did not like this headline

Only very recently HashiCorp's Terraform went a similar route. It looks like too many competitors to Terraform's cloud offering have crept up. So they decided to lock down the licensing in an attempt to hinder the competition. Terraform had previously benefited very heavily from being this open and transparent as it has become the de facto standard for infrastructure as code. Pulumi and AWS CloudFormation are the only somewhat serious competitors on that regard, but they are both much smaller or limited (as CloudFormation only works for AWS). But what do you do if everyone uses your tool, but no one is willing to pay for it and even if they do pay they choose another vendor?

In response, Terraform took a desperate measure, altering the very thing that had contributed to their success - they restricted the open source license. The community sentiment is split about this decision; some people don't care, while others are outraged. Competitors of Terraform Cloud, which had previously built upon what HashiCorp and the open source community developed, saw no alternative but to fork the repository and initiate their own project, called OpenToFu. The name isn't well liked by a lot of engineers but time will tell how this project split plays out. Did HashiCorp shoot themselves in the foot? Can the community that made terraform big in the first place also bring them down in the same fashion?

![A reddit comment about opentofu being a terrible name](./reddit_comment.png)

> Some sentiment over OpenTofu

There is more examples of license changes in the history of tech (ElasticSearch, anyone?); sometimes they go well, sometimes not. The change itself is rarely taken positively by the public and often hurts the image of a company.

These examples make me wonder, is it even worth it to build a company based on an open-source product? If the community bonus can just as easily turn into a curse, do you really want to be put at their mercy?

Some companies seem to be more successful at managing the open source offerings, the most prominent example is GitLab, I believe. GitLab was started as an open source project and only turned into a company afterwards, but it seems to manage the split rather cleanly so far. GitLab also offers a bunch of licensing options you can either use their cloud Saas hosting (with a typical free option) or you self host, but you can also self host and pay for an enterprise license. Some companies do not want their code to live in the cloud. GitLab is very intentional about what features they put in which version. But I also don't see GitLab benefiting particularly from open source contributions. Sure someone will fix a bug every now and then, but would the company work without the tool being open source? Probably yes.

I think this is a very key takeaway when deciding to go open source or not: Your business needs to work without the help of the open-source community. Yes sure, transparency is great, and if people love your software and want to help then it's amazing if they can do so. But if your product hinges on the free distribution via community building or the 'free work' from open source developers, you are playing with fire.

My co-founders and I are currently building a piece of software and we have decided to go with the open source approach for a part of our software (typical Open Core). But the goal here isn't that we want the community to do the marketing for us or build our product (although any of these effects are of course appreciated if they happen), we are building a security critical tool and security software should be secure by design not by obscurity. In this way open source is our way of fostering trust.

Moreover it will allow companies that don't have the budget to invest into security to start with a sensible safe solution without any investment. We couldn't gain these companies as a customer anyway, but letting them sit with unsafe practices is not a great solution either.

I am personally a big fan of open source as a concept, but I don't like businesses trying to take advantage of the ecosystem and community.

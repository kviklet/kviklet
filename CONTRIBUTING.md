# Contributing to Kviklet

In general we are super happy about any issues, feature requests, POC PRs or any other kind of feedback that you want to provide. We love to see Kviklet being used and we are happy to help you with any issues you might have.

If you want to contribute to the project, this document is there to help you get started. But don't worry, we are happy to help you with any questions you might have. You can reach me via email (jascha@kviklet.dev) or LinkedIn or simply via the issues section of this repository.

## Developer Guide

### Quick Start
The project consists of a spring boot kotlin backend and react typescript frontend.
To start the backend you can simple run `./gradlew bootRun` in the backend directory.
To start the frontend you'd use `npm run start`.

Additionally the project requires a postgres database. You can start one with the included docker-compose (kviklet-postgres).

### Backend
The backend architecture is roughly: Controller (Routes) -> Domain level (Service, DTOs, Adapter) -> Repository (Database Access).
The core models of the backend are ExecutionRequests and Connections. These hold all relevent data that is necessry to execute queries, start sessions etc.


#### Authorization
Authorization is handled in custom built annotations and filters. This is a bit of legacy code and more complex than it needs to be but the gist is that a `@Policy(PermissionType)` annotation on any service method is used to check if a user has the necessary permissions to call this part of the code.

The permissions are stored in the database and are assigned to roles. Roles are assigned to users. These permissions are evaluated on the fly for every request.
The `@Policy` annotation evaluates these permissions against `SecuredDomainIds` and ensures that on response level the user only gets the data he is allowed to see. For this purpose the `SecuredDomainObject` abstract class exists. In theory there is support for child and parent objects, but atm all access is managed on `Connection` level and therefore this is unused and untested.


### Frontend
The frontend is a rather plain react typescript and tailwindcss project. It doesn't make use of any state management library and uses React Contexts instead.
It uses zod for API response validation and payload generation.

This is my first somewhat serious react project and I learned a lot over time code that I write now is a lot different than at the start but some of the mess is still around.  
A bunch of files a quite large and could be split up into smaller components. There is also a lot of branching logic going on in the actual UI components. If you're confused I'm happy to help you understand the code. And no worries if you want to refactor.

### CI/CD
The project uses GitHub Actions for CI/CD. Tests run on every PR a new image is built on main branch pushes. Releases are triggered manually (except the `:main` tag which is always built).

#### Tests
The backend has a decent test coverage and I'd heavily prefer if it stays that way/improves. The frontend has rather few/no tests, but there are some E2E tests written with playwright that should ensure that at least the core workflows don't break.

#### Code Style

The backend uses ktlint for code style and the frontend uses eslint as well as prettier. Please make sure that your code is formatted correctly before submitting a PR. The CI will refuse you anyways.

To run ktlint in /backend do:
```shell
ktlint -F **/*.kt
```
If you don't have it installed yet you can do so via brew if you're on a mac:
```shell
brew install ktlint
```

To run eslint in /frontend use:
```shell
npx eslint .
```

To run prettier in /frontend use:
```shell
npx prettier --write .
```

In general have a look at the CI/CD pipeline to see what checks are run on your code and how to use them.


## What to work on?
Provided you have a feature request or a bug that you want to fix, you can simply start working on it. If you're unsure about what to work on, you can have a look at the issues section of this repository.

In general if you're doing more than a small bugfix or a small feature, it's a good idea to open an issue first to discuss the changes you want to make. This way you'd avoid doing work that might not be merged and I can also help you with choosing a sensible approach to the problem as well as help with implementation.



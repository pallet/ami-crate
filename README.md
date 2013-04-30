[Repository](https://github.com/pallet/ami-crate) &#xb7;
[Issues](https://github.com/pallet/ami-crate/issues) &#xb7;
[API docs](http://palletops.com/ami-crate/0.8/api) &#xb7;
[Annotated source](http://palletops.com/ami-crate/0.8/annotated/uberdoc.html) &#xb7;
[Release Notes](https://github.com/pallet/ami-crate/blob/develop/ReleaseNotes.md)

Crate to build an AMI from a running image.

### Dependency Information

```clj
:dependencies [[com.palletops/ami-crate "0.8.0-alpha.1"]]
```

### Releases

<table>
<thead>
  <tr><th>Pallet</th><th>Crate Version</th><th>Repo</th><th>GroupId</th></tr>
</thead>
<tbody>
  <tr>
    <th>0.8.0-beta.9</th>
    <td>0.8.0-alpha.1</td>
    <td>clojars</td>
    <td>com.palletops</td>
    <td><a href='https://github.com/pallet/ami-crate/blob/0.8.0-alpha.1/ReleaseNotes.md'>Release Notes</a></td>
    <td><a href='https://github.com/pallet/ami-crate/blob/0.8.0-alpha.1/'>Source</a></td>
  </tr>
</tbody>
</table>

## Usage

Builds an S3 backed AMI from a running instance.  Installs ruby and ami-tools,
and builds an image that excludes these.

The `server-spec` function provides a convenient pallet server spec for
ami-crate.  It takes a single map as an argument, specifying configuration
choices, as described below for the `settings` function.  You can use this
in your own group or server specs in the :extends clause.

```clj
(require '[pallet.crate.ami :as ami])
(server-spec
  :extends [(ami/server-spec
             {:image-name "pallet-test-ami"
              :image-description "Pallet test AMI"
              :s3-bucket <your-s3-bucket>
              :s3-path <your-s3-path>
              :user-id <your-aws-user-id>
              :private-key-source {:local-file <your-aws-private-key-file>}
              :certificate-source {:local-file <your-aws-cert-file>}})])
```

While `server-spec` provides an all-in-one function, you can use the individual
plan functions as you see fit.  The `server-spec` provides `:install`,
`:configure`, `:ami-bundle`, `:ami-upload`, and `:ami-register` phases.

The `settings` function provides a plan function that should be called in the
`:settings` phase.  The function puts the configuration options into the pallet
session, where they can be found by the other crate functions, or by other
crates wanting to interact with ami-crate.

The `install` function is responsible for installing and.

The `configure` function writes the credentials to the remote node..

The `ami-bundle` function builds an image bundle.

The `ami-upload` function uploads an image bundle to S3.

The `ami-register` function registers an AMI from an image bundle stored in S3.


## Testing

The crate live test is based on environment variables to provide credentials,
etc.

## Support

[On the group](http://groups.google.com/group/pallet-clj), or
[#pallet](http://webchat.freenode.net/?channels=#pallet) on freenode irc.

## License

Licensed under [EPL](http://www.eclipse.org/legal/epl-v10.html)

Copyright 2013 Hugo Duncan.

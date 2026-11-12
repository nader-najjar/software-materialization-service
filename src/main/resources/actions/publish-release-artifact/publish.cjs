const fs = require('node:fs/promises');

module.exports = async function publish({github}) {
  const [owner, repo] = process.env.REPOSITORY_NAME.split('/');
  const repository = {owner, repo};
  const tag = process.env.RELEASE_NAME;
  const release = await getOrCreateRelease(github, repository, tag);
  await uploadAsset(github, repository, release);
  if (release.draft) {
    await github.rest.repos.updateRelease({...repository, release_id: release.id, draft: false, make_latest: 'false'});
  }
};

async function getOrCreateRelease(github, repository, tag) {
  try {
    const response = await github.rest.repos.getReleaseByTag({...repository, tag});
    return response.data;

  } catch (error) {
    if (error.status !== 404) { throw error; }
  }

  const draft = await findDraftRelease(github, repository, tag);
  if (draft) { return draft; }

  const response = await github.rest.repos.createRelease({
    ...repository,
    tag_name: tag,
    target_commitish: process.env.COMMIT_SHA,
    name: tag,
    draft: true
  });
  return response.data;
}

async function uploadAsset(github, repository, release) {
  const name = `${process.env.RELEASE_NAME}.tar.gz`;
  const existing = release.assets.find(asset => asset.name === name);
  if (existing?.state === 'uploaded') { return; }
  if (existing) {
    await github.rest.repos.deleteReleaseAsset({...repository, asset_id: existing.id});
  }

  const data = await fs.readFile('build/release-artifact.tar.gz');
  await github.rest.repos.uploadReleaseAsset({
    ...repository,
    release_id: release.id,
    name,
    data,
    headers: {'content-type': 'application/gzip', 'content-length': data.length}
  });
}

async function findDraftRelease(github, repository, tag) {
  const pages = github.paginate.iterator(github.rest.repos.listReleases, {...repository, per_page: 100});
  for await (const page of pages) {
    const draft = page.data.find(release => release.draft && release.tag_name === tag);
    if (draft) { return draft; }
  }
  return undefined;
}

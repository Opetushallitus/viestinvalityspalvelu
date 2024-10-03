const fs = require('node:fs/promises');
const { faker } = require('@faker-js/faker/locale/fi');

function getTestDatum() {
    return {
        etunimi: faker.person.firstName(),
        sukunimi: faker.person.lastName(),
        otsikko: faker.lorem.sentences(1),
        sisalto: faker.lorem.sentences({ min: 5, max: 50})
    }
}

async function writeTestData() {
    const testdata = []
    for(var i=0;i<150000;i++) {
        testdata[i] = getTestDatum();
    }

    try {
        await fs.writeFile('./testdata.json', '');
        await fs.appendFile('./testdata.json', JSON.stringify(testdata, null, 2));
    } catch (err) {
        console.log(err);
    }
}

writeTestData();




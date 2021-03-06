/**
 * simpleQuery javascript functions
 *
 * @author  Vincent Ludden
 * @since   20100526
 * @package query
 *
 * Revision information:
 * $Rev$
 * $Author$
 * $Date$
 */

  $(document).ready(function() {

    var compoundCounter = 2;
    var transcriptomeCounter = 2;

    $("#accordion").accordion({collapsible: true,
        autoHeight: false,
        active: 0});

    $('#addCompound').click(function() {
        var compoundGroup = document.getElementById('compoundGroup');
        var newCompoundDiv = document.createElement('div');
        newCompoundDiv.setAttribute('id', 'compoundRow' + compoundCounter);

        var newCompoundRowDiv1 = document.createElement('div');
        newCompoundRowDiv1.setAttribute('class', 'descriptionSA');
        newCompoundRowDiv1.innerHTML = "Compound";
        newCompoundDiv.appendChild(newCompoundRowDiv1);

        var newCompoundRowDiv2 = document.createElement('div');
        newCompoundRowDiv2.setAttribute('class', 'input');
        newCompoundRowDiv2.innerHTML = '<input type="text" name="sa_compound" value="">';
        newCompoundDiv.appendChild(newCompoundRowDiv2);

        var newCompoundRowDiv3 = document.createElement('div');
        newCompoundRowDiv3.setAttribute('class', 'descriptionSA');
        newCompoundRowDiv3.innerHTML = "Operator";
        newCompoundDiv.appendChild(newCompoundRowDiv3);

        var newCompoundRowDiv4 = document.createElement('div');
        newCompoundRowDiv4.setAttribute('class', 'input');
        var newSelectBox = document.getElementById('operatorInput');
        newCompoundRowDiv4 = newSelectBox.cloneNode(true);
        newCompoundDiv.appendChild(newCompoundRowDiv4);

        var newCompoundRowDiv5 = document.createElement('div');
        newCompoundRowDiv5.setAttribute('class', 'descriptionSA');
        newCompoundRowDiv5.innerHTML = "Value";
        newCompoundDiv.appendChild(newCompoundRowDiv5);

        var newCompoundRowDiv6 = document.createElement('div');
        newCompoundRowDiv6.setAttribute('class', 'input');
        newCompoundRowDiv6.innerHTML = "<input type='text' name='sa_value' value=''>";
        newCompoundDiv.appendChild(newCompoundRowDiv6); 

        //var newCompoundRowDiv7 = document.createElement('div');
        //newCompoundRowDiv7.setAttribute('class', 'delete');
        //newCompoundRowDiv7.innerHTML = "delete";
        //newCompoundDiv.appendChild(newCompoundRowDiv7);
        compoundGroup.appendChild(newCompoundDiv);

        compoundCounter++;
        // alert('Handler for adding compound called: ' + compoundCounter);
        return true;
    });

      $('#addTranscriptome').click(function() {
          var transcriptomeGroup = document.getElementById('transcriptomeGroup');
          var newTranscriptomeDiv = document.createElement('div');
          newTranscriptomeDiv.setAttribute('id', 'transcriptomeRow' + transcriptomeCounter);

          var newTranscriptomeRowDiv1 = document.createElement('div');
          newTranscriptomeRowDiv1.setAttribute('class', 'description');
          newTranscriptomeRowDiv1.innerHTML = "Gene/pathway";
          newTranscriptomeDiv.appendChild(newTranscriptomeRowDiv1);

          var newTranscriptomeRowDiv2 = document.createElement('div');
          newTranscriptomeRowDiv2.setAttribute('class', 'input');
          newTranscriptomeRowDiv2.innerHTML = '<input type="text" name="genepath" value="">';
          newTranscriptomeDiv.appendChild(newTranscriptomeRowDiv2);

          var newTranscriptomeRowDiv3 = document.createElement('div');
          newTranscriptomeRowDiv3.setAttribute('class', 'description');
          newTranscriptomeRowDiv3.innerHTML = "Type of regulations";
          newTranscriptomeDiv.appendChild(newTranscriptomeRowDiv3);

          var newTranscriptomeRowDiv4 = document.createElement('div');
          newTranscriptomeRowDiv4.setAttribute('class', 'input');
          var newSelectBox = document.getElementById('regulationInput');
          newTranscriptomeRowDiv4 = newSelectBox.cloneNode(true);
          newTranscriptomeRowDiv4.setAttribute('id', 'regulation' + transcriptomeCounter);
          newTranscriptomeDiv.appendChild(newTranscriptomeRowDiv4);

          transcriptomeGroup.appendChild(newTranscriptomeDiv);

          transcriptomeCounter++;
          // alert('Handler for adding transcriptome called: ' + transcriptomeCounter);
          return true;
      });

  });

